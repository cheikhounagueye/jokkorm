package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.master.MasterSyncService;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Étiquettes d'emplacement (mode magasin, pattern 12) : une par POSITION sous le nœud choisi
 * (salle, travée, tablette ou emplacement), sur planche Avery L7162 (99,1 × 33,9 mm, 16 par A4),
 * format allongé qui se colle sur le chant d'une tablette.
 *
 * <p>Chaque étiquette porte le QR du code (lu par l'iPhone), le même code en Code128 (lu par les
 * douchettes 1D), le code en clair et l'adresse lisible. Le code ne change jamais : on peut
 * réimprimer sans recoller les autres.
 */
@Service
public class PositionLabelsPdfGenerator {

    private static final float MM = 2.83465f;
    private static final AveryTemplate TEMPLATE = AveryTemplate.AVERY_L7162;

    private final StorageLocationRepository locations;
    private final BarcodeImageGenerator barcodes;
    private final MasterSyncService masterSync;
    private final AuditChainService audit;

    public PositionLabelsPdfGenerator(
        StorageLocationRepository locations,
        BarcodeImageGenerator barcodes,
        MasterSyncService masterSync,
        AuditChainService audit) {
        this.locations = locations;
        this.barcodes = barcodes;
        this.masterSync = masterSync;
        this.audit = audit;
    }

    /** Un emplacement à étiqueter : code imprimé et adresse lisible. */
    public record PositionLabel(UUID id, String path, String scanCode, String address) {}

    @Transactional(readOnly = true)
    public List<PositionLabel> list(UUID nodeId) {
        TenantId tenant = TenantContext.current();
        StorageLocation node = locations.findById(tenant, nodeId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(nodeId, tenant.value())));
        return locations.findPositionsUnder(tenant, node.path()).stream()
            .map(p -> new PositionLabel(p.id(), p.path(), p.scanCode().orElseThrow(), masterSync.address(tenant, p)))
            .toList();
    }

    @Transactional
    public byte[] generate(UUID nodeId, String actor) {
        List<PositionLabel> labels = list(nodeId);
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Aucun emplacement sous ce nœud : rien à imprimer");
        }
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

        float w = (float) TEMPLATE.widthMm() * MM, h = (float) TEMPLATE.heightMm() * MM;
        float left = (float) TEMPLATE.marginLeftMm() * MM, top = (float) TEMPLATE.marginTopMm() * MM;
        float gapH = (float) TEMPLATE.horizontalGapMm() * MM, gapV = (float) TEMPLATE.verticalGapMm() * MM;
        float qr = 27 * MM, pad = 3 * MM;

        try (PDDocument doc = new PDDocument()) {
            PDPageContentStream content = null;
            try {
                for (int i = 0; i < labels.size(); i++) {
                    PositionLabel label = labels.get(i);
                    int slot = i % TEMPLATE.perPage();
                    if (slot == 0) {
                        if (content != null) content.close();
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        content = new PDPageContentStream(doc, page);
                    }
                    float x = left + (slot % TEMPLATE.columns()) * (w + gapH);
                    float yTop = PDRectangle.A4.getHeight() - top - (slot / TEMPLATE.columns()) * (h + gapV);
                    float yBottom = yTop - h;

                    PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc,
                        barcodes.render(label.scanCode(), Symbology.QR, 300, 300, false), label.scanCode() + "-qr.png");
                    content.drawImage(qrImage, x + pad, yBottom + (h - qr) / 2, qr, qr);

                    float textX = x + pad + qr + pad;
                    float textW = x + w - pad - textX;
                    String[] where = splitAddress(label.address());
                    text(content, regular, 7, textX, yTop - 13, fit(regular, 7, where[0], textW));
                    text(content, bold, 10.5f, textX, yTop - 27, fit(bold, 10.5f, where[1], textW));
                    text(content, bold, 14, textX, yTop - 44, fit(bold, 14, where[2], textW));

                    PDImageXObject bars = PDImageXObject.createFromByteArray(doc,
                        barcodes.render(label.scanCode(), Symbology.CODE128, 600, 120, false), label.scanCode() + "-128.png");
                    content.drawImage(bars, textX, yBottom + 13, textW, 20);
                    text(content, mono, 7.5f, textX, yBottom + 5, label.scanCode());
                }
            } finally {
                if (content != null) content.close();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            audit.record(AuditAction.POSITION_LABELS_PRINTED, AuditTargetType.STORAGE_LOCATION, nodeId, actor,
                "positions=%d template=%s".formatted(labels.size(), TEMPLATE.name()));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Position labels PDF render failed for node " + nodeId, e);
        }
    }

    /**
     * « Salle B (SALLE-B) · Épi E1 · rang 3 · face A · Travée T04 · Tablette 1 · Emplacement 01 »
     * → [salle et épi, « Travée T04 · Tablette 1 », « Emplacement 01 »].
     */
    static String[] splitAddress(String address) {
        List<String> parts = new ArrayList<>(Arrays.asList(address.split(" · ")));
        String slot = parts.isEmpty() ? "" : parts.remove(parts.size() - 1);
        List<String> shelf = new ArrayList<>();
        while (!parts.isEmpty() && shelf.size() < 2
            && (parts.get(parts.size() - 1).startsWith("Tablette ") || parts.get(parts.size() - 1).startsWith("Travée "))) {
            shelf.add(0, parts.remove(parts.size() - 1));
        }
        return new String[] { String.join(" · ", parts), String.join(" · ", shelf), slot };
    }

    private static void text(PDPageContentStream content, PDFont font, float size, float x, float y, String s)
        throws IOException {
        if (s.isEmpty()) return;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(s);
        content.endText();
    }

    /** Garde les caractères que la police sait écrire, puis coupe avec « … » si trop long. */
    static String fit(PDFont font, float size, String raw, float maxWidth) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        String s = sb.toString();
        if (width(font, size, s) <= maxWidth) return s;
        while (s.length() > 1 && width(font, size, s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s.stripTrailing() + "…";
    }

    private static float width(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }
}
