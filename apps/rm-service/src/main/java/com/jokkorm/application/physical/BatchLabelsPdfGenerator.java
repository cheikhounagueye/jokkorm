package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.ContainerBatch;
import com.jokkorm.domain.physical.ContainerBatchRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Génère un PDF A4 multi-pages contenant toutes les étiquettes code-barres d'un lot,
 * selon un template Avery donné (grille, taille, marges). Phase 5M.2.
 *
 * <p>Chaque étiquette contient :
 * <ul>
 *   <li>Titre : le libellé de la boîte (ex. « Marchés publics DMG · 023/500 »)</li>
 *   <li>Image barcode PNG rendue via {@link BarcodeImageGenerator}</li>
 *   <li>Contenu du barcode en texte sous l'image (toujours — pour lecture humaine)</li>
 * </ul>
 *
 * <p>Marque le lot comme {@code PRINTED} à chaque génération (update firstPrintedAt +
 * lastPrintedAt + audit CONTAINER_BATCH_PRINTED).
 */
@Service
public class BatchLabelsPdfGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BatchLabelsPdfGenerator.class);
    private static final float MM_TO_POINTS = 2.83465f;

    private final ContainerBatchRepository batches;
    private final BarcodeImageGenerator barcodeImage;
    private final AuditChainService audit;
    private final Clock clock;

    public BatchLabelsPdfGenerator(
        ContainerBatchRepository batches,
        BarcodeImageGenerator barcodeImage,
        AuditChainService audit,
        Clock clock) {
        this.batches = batches;
        this.barcodeImage = barcodeImage;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public byte[] generate(UUID batchId, AveryTemplate template, Symbology symbology, String actor) {
        TenantId tenant = TenantContext.current();
        ContainerBatch batch = batches.findById(tenant, batchId)
            .orElseThrow(() -> new BatchNotFoundException(
                "Batch %s not found (tenant=%s)".formatted(batchId, tenant.value())));

        List<PhysicalContainer> containers = batches.listContainers(tenant, batchId);
        if (containers.isEmpty()) {
            throw new IllegalStateException(
                "Batch %s has no containers — refusing to render empty PDF".formatted(batchId));
        }

        try (PDDocument doc = new PDDocument()) {
            float labelWidthPt = (float) (template.widthMm() * MM_TO_POINTS);
            float labelHeightPt = (float) (template.heightMm() * MM_TO_POINTS);
            float marginLeftPt = (float) (template.marginLeftMm() * MM_TO_POINTS);
            float marginTopPt = (float) (template.marginTopMm() * MM_TO_POINTS);
            float gapHPt = (float) (template.horizontalGapMm() * MM_TO_POINTS);
            float gapVPt = (float) (template.verticalGapMm() * MM_TO_POINTS);
            int perPage = template.perPage();

            // Paramètres de rendu du code-barres image : dimensions adaptées à l'étiquette
            int barcodeWidthPx = symbology == Symbology.QR ? 150 : 400;
            int barcodeHeightPx = symbology == Symbology.QR ? 150 : 100;

            PDPage currentPage = null;
            PDPageContentStream content = null;
            try {
                for (int i = 0; i < containers.size(); i++) {
                    PhysicalContainer c = containers.get(i);
                    int posOnPage = i % perPage;

                    if (posOnPage == 0) {
                        if (content != null) content.close();
                        currentPage = new PDPage(PDRectangle.A4);
                        doc.addPage(currentPage);
                        content = new PDPageContentStream(doc, currentPage);
                    }

                    int col = posOnPage % template.columns();
                    int row = posOnPage / template.columns();

                    // Coordonnées PDF : Y part du bas, donc on inverse.
                    float x = marginLeftPt + col * (labelWidthPt + gapHPt);
                    float yTop = PDRectangle.A4.getHeight() - marginTopPt - row * (labelHeightPt + gapVPt);
                    float yBottom = yTop - labelHeightPt;

                    // Rendu du barcode PNG dans un buffer
                    byte[] png = barcodeImage.render(
                        c.barcode(), symbology, barcodeWidthPx, barcodeHeightPx, true);
                    PDImageXObject image = PDImageXObject.createFromByteArray(doc, png,
                        c.barcode() + ".png");

                    // Positionnement : barcode centré horizontalement, calé en haut
                    float imgW = labelWidthPt * 0.9f;
                    float imgH = labelHeightPt * 0.65f;
                    float imgX = x + (labelWidthPt - imgW) / 2;
                    float imgY = yBottom + labelHeightPt - imgH - 4;
                    content.drawImage(image, imgX, imgY, imgW, imgH);

                    // Libellé humain en haut (si présent)
                    if (c.label() != null && !c.label().isBlank()) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 7);
                        content.newLineAtOffset(x + 4, yTop - 10);
                        String label = truncate(c.label(), 48);
                        content.showText(sanitize(label));
                        content.endText();
                    }

                    // Phase 5N — N° séquence dans le lot (haut-droite) :
                    //   format "N° 023/500" aligné pour lecture rapide humaine
                    if (c.sequenceInBatch().isPresent()) {
                        String seq = "N° %03d/%03d".formatted(c.sequenceInBatch().get(), batch.size());
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
                        content.newLineAtOffset(x + labelWidthPt - 42, yTop - 10);
                        content.showText(seq);
                        content.endText();
                    }

                    // Phase 5N — Référence externe prestataire en bas-gauche si présente
                    if (c.externalReference().isPresent()) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 5);
                        content.newLineAtOffset(x + 4, yBottom + 3);
                        content.showText(sanitize("Ref. " + c.externalReference().get()));
                        content.endText();
                    }
                }
            } finally {
                if (content != null) content.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);

            // Marque le lot PRINTED + audit
            batch.markPrinted(clock.instant());
            batches.save(batch);
            audit.record(
                AuditAction.CONTAINER_BATCH_PRINTED,
                AuditTargetType.CONTAINER_BATCH,
                batch.id(),
                actor,
                "template=%s symbology=%s pages=%d containers=%d"
                    .formatted(template.name(), symbology.name(),
                        (int) Math.ceil(containers.size() / (double) perPage),
                        containers.size()));

            LOG.info("Rendered batch {} labels : template={} symbology={} containers={}",
                batchId, template.name(), symbology.name(), containers.size());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF render failed for batch " + batchId, e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** PDFBox Helvetica ne supporte pas tous les caractères — on retire les points hors ANSI. */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 127) sb.append(c);
            else if (c == 'à' || c == 'â') sb.append('a');
            else if (c == 'é' || c == 'è' || c == 'ê') sb.append('e');
            else if (c == 'ç') sb.append('c');
            else if (c == 'î' || c == 'ï') sb.append('i');
            else if (c == 'ô') sb.append('o');
            else if (c == 'ù' || c == 'û') sb.append('u');
            else if (c == '°') sb.append('o');
            else sb.append('?');
        }
        return sb.toString();
    }
}
