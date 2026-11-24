package com.jokkorm.application.physical;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Texte imprimable avec les polices standard du PDF (Helvetica, encodage WinAnsi) : accents,
 * « · », guillemets français passent tels quels ; un caractère hors de la police devient « ? »
 * au lieu de faire échouer l'impression.
 */
public final class PdfText {

    /** L'encodage WinAnsi est le même pour Helvetica et toutes ses variantes. */
    private static final PDType1Font PROBE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private PdfText() {}

    public static String printable(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isISOControl(c)) continue;
            try {
                PROBE.encode(String.valueOf(c));
                sb.append(c);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
