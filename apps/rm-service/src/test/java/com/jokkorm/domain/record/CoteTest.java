package com.jokkorm.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoteTest {

    @Test
    @DisplayName("accepte une cote valide avec slashes")
    void accepts_slashed_cote() {
        assertThat(Cote.of("DMG/MP/2024/047").value()).isEqualTo("DMG/MP/2024/047");
    }

    @Test
    @DisplayName("normalise en majuscules")
    void normalizes_to_upper() {
        assertThat(Cote.of("dmg/mp/2024/047").value()).isEqualTo("DMG/MP/2024/047");
    }

    @Test
    @DisplayName("accepte les tirets et underscores")
    void accepts_hyphens_underscores() {
        assertThat(Cote.of("2024-FIN-042").value()).isEqualTo("2024-FIN-042");
        assertThat(Cote.of("RH_CONTRATS_2024").value()).isEqualTo("RH_CONTRATS_2024");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("rejette null et blancs")
    void rejects_blank(String blank) {
        assertThatThrownBy(() -> Cote.of(blank)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejette null")
    void rejects_null() {
        assertThatThrownBy(() -> Cote.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "DMG MP 2024",       // espace
            "DMG.MP.2024",       // point
            "DMG@MP/2024",       // @
            "DMG\\MP\\2024",     // backslash
            "accentué"           // unicode
        })
    @DisplayName("rejette les caractères hors charset")
    void rejects_invalid_charset(String invalid) {
        assertThatThrownBy(() -> Cote.of(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejette > 64 caractères")
    void rejects_too_long() {
        String tooLong = "A".repeat(65);
        assertThatThrownBy(() -> Cote.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("égalité de valeur")
    void value_equality() {
        assertThat(Cote.of("ABC/123")).isEqualTo(Cote.of("abc/123"));
    }
}
