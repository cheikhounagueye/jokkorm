package com.jokkorm.application.physical;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeImageGeneratorTest {

    private final BarcodeImageGenerator generator = new BarcodeImageGenerator();

    @ParameterizedTest(name = "{0} {1}×{2}")
    @CsvSource({"QR, 800, 800", "QR, 300, 300", "CODE128, 600, 180", "CODE128, 1200, 360"})
    @DisplayName("Le texte en clair tient en entier : les bords de la bande de texte restent blancs")
    void humanTextNeverOverflows(Symbology symbology, int width, int height) throws IOException {
        byte[] png = generator.render("BOX-261122-KN3D3S", symbology, width, height, true);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        int h = img.getHeight();
        int band = Math.max(18, h / 6);
        int dark = 0;
        for (int y = h - band; y < h; y++) {
            for (int x : new int[] {0, 1, img.getWidth() - 2, img.getWidth() - 1}) {
                assertThat(isWhite(img.getRGB(x, y))).as("pixel (%d,%d) au bord de la bande de texte", x, y).isTrue();
            }
            for (int x = 0; x < img.getWidth(); x++) {
                if (!isWhite(img.getRGB(x, y))) dark++;
            }
        }
        assertThat(dark).as("le texte est bien dessiné").isPositive();
    }

    private static boolean isWhite(int rgb) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return r > 200 && g > 200 && b > 200;
    }
}
