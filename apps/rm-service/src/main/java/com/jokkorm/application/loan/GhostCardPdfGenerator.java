package com.jokkorm.application.loan;

import com.jokkorm.application.physical.BarcodeImageGenerator;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5E — génère le "fantôme" (ghost card) : carton A5 portrait que
 * l'archiviste imprime et dépose dans l'emplacement de la boîte sortie.
 * Permet de retrouver visuellement une sortie sans ouvrir l'application.
 *
 * <p>Contenu : barcode de la boîte (CODE128), QR contenant le loan id,
 * emprunteur, service, date de retour prévue, référence externe si présente.
 */
@Service
public class GhostCardPdfGenerator {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter
        .ofPattern("dd/MM/yyyy", Locale.FRENCH).withZone(ZoneId.systemDefault());

    private final PhysicalLoanRepository loans;
    private final PhysicalContainerRepository containers;
    private final BarcodeImageGenerator barcodeImage;

    public GhostCardPdfGenerator(
        PhysicalLoanRepository loans,
        PhysicalContainerRepository containers,
        BarcodeImageGenerator barcodeImage) {
        this.loans = loans;
        this.containers = containers;
        this.barcodeImage = barcodeImage;
    }

    @Transactional(readOnly = true)
    public byte[] generate(UUID loanId) {
        TenantId tenant = TenantContext.current();
        PhysicalLoan loan = loans.findById(tenant, loanId)
            .orElseThrow(() -> new LoanNotFoundException("Loan %s not found".formatted(loanId)));
        PhysicalContainer container = containers.findById(tenant, loan.containerId())
            .orElseThrow(() -> new IllegalStateException(
                "Container %s of loan %s not found (DB inconsistency)"
                    .formatted(loan.containerId(), loanId)));

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PDRectangle.A5.getWidth();
                float h = PDRectangle.A5.getHeight();

                // Bandeau "SORTIE" en haut
                cs.setNonStrokingColor(237f / 255, 108f / 255, 2f / 255); // orange soutenu
                cs.addRect(0, h - 60, w, 60);
                cs.fill();

                cs.beginText();
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 28);
                cs.newLineAtOffset(30, h - 42);
                cs.showText("BOITE SORTIE");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(w - 160, h - 30);
                cs.showText("Fantome pret physique");
                cs.endText();

                cs.setNonStrokingColor(0f, 0f, 0f);

                // Barcode CODE128 de la boîte
                byte[] barcodePng = barcodeImage.render(container.barcode(), Symbology.CODE128, 500, 100, true);
                PDImageXObject barcodeImg = PDImageXObject.createFromByteArray(doc, barcodePng, "bc.png");
                cs.drawImage(barcodeImg, 30, h - 170, w - 60, 90);

                // Infos clés — libellé boîte
                String containerLabel = container.label() != null && !container.label().isBlank()
                    ? container.label() : "(sans libellé)";
                drawLine(cs, "Boite", sanitize(containerLabel), 30, h - 200, 10, true);

                drawLine(cs, "Emprunteur", sanitize(loan.borrowerName()), 30, h - 230, 12, true);
                drawLine(cs, "Service", sanitize(loan.borrowerDept().orElse("—")), 30, h - 255, 10, false);
                drawLine(cs, "Motif",
                    sanitize(loan.purpose().orElse("—")), 30, h - 275, 9, false);

                drawLine(cs, "Sortie le",
                    DATE_FR.format(loan.outAt().orElse(loan.requestedAt())),
                    30, h - 310, 11, true);
                drawLine(cs, "Retour prévu le",
                    DATE_FR.format(loan.expectedReturnAt()),
                    30, h - 335, 14, true);

                container.externalReference().ifPresent(ref -> {
                    try {
                        drawLine(cs, "Ref. ext.", sanitize(ref), 30, h - 360, 9, false);
                    } catch (IOException e) { throw new RuntimeException(e); }
                });

                // QR code en bas-droite : loan id + container barcode
                String qrPayload = "jokkorm:loan:%s:%s"
                    .formatted(loan.id(), container.barcode());
                byte[] qrPng = barcodeImage.render(qrPayload, Symbology.QR, 120, 120, false);
                PDImageXObject qrImg = PDImageXObject.createFromByteArray(doc, qrPng, "qr.png");
                cs.drawImage(qrImg, w - 140, 20, 120, 120);

                // Footer
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 7);
                cs.newLineAtOffset(30, 25);
                cs.showText("Scanner le QR pour acceder au pret dans JokkoRM.");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 7);
                cs.newLineAtOffset(30, 15);
                cs.showText("Loan ID : " + loan.id());
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Ghost card PDF render failed for loan " + loanId, e);
        }
    }

    private static void drawLine(PDPageContentStream cs, String label, String value,
                                  float x, float y, int size, boolean bold) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(label) + " : ");
        cs.endText();
        cs.beginText();
        cs.setFont(new PDType1Font(bold
            ? Standard14Fonts.FontName.HELVETICA_BOLD
            : Standard14Fonts.FontName.HELVETICA), size);
        cs.newLineAtOffset(x + 85, y);
        cs.showText(value);
        cs.endText();
    }

    private static String sanitize(String s) {
        if (s == null) return "";
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
