package com.jokkorm.application.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AveryTemplateTest {

    @Test
    @DisplayName("Les planches tiennent dans une feuille A4 (210 × 297 mm), et la L7162 y est centrée")
    void templatesFitA4() {
        for (AveryTemplate t : AveryTemplate.values()) {
            double width = t.marginLeftMm() + t.columns() * t.widthMm() + (t.columns() - 1) * t.horizontalGapMm();
            double height = t.marginTopMm() + t.rows() * t.heightMm() + (t.rows() - 1) * t.verticalGapMm();
            assertThat(width).as("%s largeur", t).isLessThanOrEqualTo(210.0);
            assertThat(height).as("%s hauteur", t).isLessThanOrEqualTo(297.0);
        }
        AveryTemplate l7162 = AveryTemplate.AVERY_L7162;
        double right = 210 - (l7162.marginLeftMm() + 2 * l7162.widthMm() + l7162.horizontalGapMm());
        double bottom = 297 - (l7162.marginTopMm() + 8 * l7162.heightMm());
        assertThat(right).isCloseTo(l7162.marginLeftMm(), org.assertj.core.data.Offset.offset(0.001));
        assertThat(bottom).isCloseTo(l7162.marginTopMm(), org.assertj.core.data.Offset.offset(0.001));
        assertThat(l7162.perPage()).isEqualTo(16);
    }

    @Test
    @DisplayName("L'ancien nom AVERY_L7169 désigne la L7162 ; noms courts et casse tolérés")
    void parseAcceptsLegacyName() {
        assertThat(AveryTemplate.parse("AVERY_L7169")).isEqualTo(AveryTemplate.AVERY_L7162);
        assertThat(AveryTemplate.parse("l7162")).isEqualTo(AveryTemplate.AVERY_L7162);
        assertThat(AveryTemplate.parse(" AVERY_L7160 ")).isEqualTo(AveryTemplate.AVERY_L7160);
        assertThatThrownBy(() -> AveryTemplate.parse("L9999")).isInstanceOf(IllegalArgumentException.class);
    }
}
