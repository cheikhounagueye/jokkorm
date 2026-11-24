package com.jokkorm.application.physical;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Génère l'image PNG d'un code-barres pour impression sur étiquette (phase 5L).
 *
 * <p>Le <b>contenu</b> du barcode ({@code BOX-YYMMDD-xxxxxx}) est fixé par
 * {@link BarcodeGenerator} à la création de la boîte et reste immuable. Ce service
 * ne touche qu'au <b>rendu</b> visuel : symbologie, dimensions, texte humain visible
 * en-dessous. Chaque appel génère une image fraîche depuis le contenu immuable.
 *
 * <p>Symbologies supportées :
 * <ul>
 *   <li>{@link Symbology#CODE128} — barcode 1D classique, lu par toutes les douchettes
 *       USB/Bluetooth du marché. Format recommandé pour étiquette de boîte.</li>
 *   <li>{@link Symbology#QR} — code 2D, lu par smartphones et scanners modernes. Plus
 *       dense, résiste mieux aux salissures grâce à la correction d'erreurs.</li>
 * </ul>
 */
@Service
public class BarcodeImageGenerator {

    /** Blanc gardé de chaque côté du texte en clair. */
    static final int TEXT_SIDE_MARGIN_PX = 4;
    private static final int MIN_TEXT_SIZE_PX = 8;

    public enum Symbology {
        CODE128(BarcodeFormat.CODE_128),
        QR(BarcodeFormat.QR_CODE);

        final BarcodeFormat zxingFormat;
        Symbology(BarcodeFormat f) { this.zxingFormat = f; }
    }

    /**
     * @param content   chaîne immuable (ex. {@code BOX-261122-A3F2K9})
     * @param symbology CODE128 ou QR
     * @param widthPx   largeur en pixels (100–2000)
     * @param heightPx  hauteur en pixels (100–2000) — ignorée pour QR (carré)
     * @param showLabel afficher le contenu en texte humain sous le barcode
     * @return PNG bytes
     */
    public byte[] render(String content, Symbology symbology,
                          int widthPx, int heightPx, boolean showLabel) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        int w = clamp(widthPx, 100, 2000);
        int h = clamp(heightPx, 100, 2000);
        if (symbology == Symbology.QR) {
            h = w; // QR est carré
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 2);
            if (symbology == Symbology.QR) {
                hints.put(EncodeHintType.ERROR_CORRECTION,
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            }

            // Hauteur réservée pour le libellé humain si showLabel
            int labelHeight = showLabel ? Math.max(18, h / 6) : 0;
            int barcodeHeight = h - labelHeight;

            BitMatrix matrix = new MultiFormatWriter()
                .encode(content, symbology.zxingFormat, w, barcodeHeight, hints);
            BufferedImage barcodeImg = MatrixToImageWriter.toBufferedImage(matrix);

            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(barcodeImg, 0, 0, null);
                if (showLabel) {
                    g.setColor(Color.BLACK);
                    // Le texte en clair tient toujours en entier dans la largeur : sur un QR (carré),
                    // la taille tirée de la hauteur le faisait déborder (« BOX-261122- » tronqué).
                    int size = Math.max(10, labelHeight - 6);
                    int maxWidth = w - 2 * TEXT_SIDE_MARGIN_PX;
                    Font font = new Font(Font.MONOSPACED, Font.BOLD, size);
                    g.setFont(font);
                    var fm = g.getFontMetrics();
                    while (fm.stringWidth(content) > maxWidth && size > MIN_TEXT_SIZE_PX) {
                        size--;
                        font = font.deriveFont((float) size);
                        g.setFont(font);
                        fm = g.getFontMetrics();
                    }
                    int textWidth = fm.stringWidth(content);
                    int x = Math.max(0, (w - textWidth) / 2);
                    int y = barcodeHeight + ((labelHeight + fm.getAscent()) / 2) - 2;
                    g.drawString(content, x, y);
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Barcode render failed for " + content, e);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
