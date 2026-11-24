package com.jokkorm.application.physical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PdfTextTest {

    @Test
    @DisplayName("Accents, « · » et guillemets français s'impriment tels quels ; hors police → « ? »")
    void keepsWhatHelveticaCanPrint() {
        assertThat(PdfText.printable("Marchés publics DMG · 004/020")).isEqualTo("Marchés publics DMG · 004/020");
        assertThat(PdfText.printable("Boîte « DRH » — prêt")).isEqualTo("Boîte « DRH » — prêt");
        assertThat(PdfText.printable("Flèche → ici")).isEqualTo("Flèche ? ici");
        assertThat(PdfText.printable("ligne\nsuivante")).isEqualTo("lignesuivante");
        assertThat(PdfText.printable(null)).isEmpty();
    }
}
