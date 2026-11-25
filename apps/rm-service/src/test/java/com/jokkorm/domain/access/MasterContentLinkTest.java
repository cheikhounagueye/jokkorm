package com.jokkorm.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pattern 07 : JokkoRM ne garde que la référence d'une numérisation, jamais le fichier. */
class MasterContentLinkTest {

    private static final String MAARCH = "https://maarch.example.org";

    @Test
    @DisplayName("Référence seule : acceptée, espaces autour retirés, pas de lien")
    void referenceOnly() {
        MasterContentLink link = MasterContentLink.of("  FR094080_2026_AE0002_001 ", null, MAARCH);
        assertThat(link.reference()).isEqualTo("FR094080_2026_AE0002_001");
        assertThat(link.url()).isNull();
        assertThat(MasterContentLink.of("X", "   ", MAARCH).url()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Sans référence : refusé (le scan doit pouvoir être retrouvé chez le maître)")
    void referenceRequired(String blank) {
        assertThatThrownBy(() -> MasterContentLink.of(blank, MAARCH + "/x", MAARCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("référence");
        assertThatThrownBy(() -> MasterContentLink.of(null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Référence trop longue ou avec un caractère invisible : refusée")
    void referenceShape() {
        assertThatThrownBy(() -> MasterContentLink.of("A".repeat(201), null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(MasterContentLink.of("A".repeat(200), null, null).reference()).hasSize(200);
        assertThatThrownBy(() -> MasterContentLink.of("AE0002\u0000_001", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterContentLink.of("AE0002\n001", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Lien vers le maître (même hôte, casse ignorée, port par défaut) : accepté tel quel")
    void linkToMaster() {
        String url = "HTTPS://Maarch.Example.org:443/recordsManagement/archiveDescription/abc";
        assertThat(MasterContentLink.of("AE0002", url, MAARCH).url()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "file:///etc/passwd",
        "ftp://maarch.example.org/x",
        "//maarch.example.org/x",
        "maarch.example.org/x",
        "https://",
        "https://exa mple.org/x",
    })
    @DisplayName("Lien qui n'est pas une adresse web http(s) : refusé (il serait cliqué par le demandeur)")
    void notAWebLink(String url) {
        assertThatThrownBy(() -> MasterContentLink.of("AE0002", url, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Lien avec identifiant ou mot de passe : refusé")
    void noCredentials() {
        assertThatThrownBy(() -> MasterContentLink.of("AE0002", "https://user:secret@maarch.example.org/x", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mot de passe");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://maarch.example.org.evil.com/x",
        "https://evil.com/maarch.example.org/x",
        "http://maarch.example.org/x",
        "https://maarch.example.org:8443/x",
    })
    @DisplayName("Lien vers un autre site que le maître configuré : refusé")
    void otherOrigin(String url) {
        assertThatThrownBy(() -> MasterContentLink.of("AE0002", url, MAARCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(MAARCH);
    }

    @Test
    @DisplayName("Maître publié sous un sous-chemin : le lien doit rester dessous, « .. » compris")
    void subPath() {
        String base = "https://hote.example.org/maarch/";
        assertThat(MasterContentLink.of("R", "https://hote.example.org/maarch/archive/1", base).url()).isNotNull();
        assertThat(MasterContentLink.of("R", "https://hote.example.org/maarch", base).url()).isNotNull();
        assertThatThrownBy(() -> MasterContentLink.of("R", "https://hote.example.org/maarchx/1", base))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterContentLink.of("R", "https://hote.example.org/maarch/../autre", base))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Maître sans interface (démonstration) : tout lien http(s) accepté")
    void noOrigin() {
        assertThat(MasterContentLink.of("R", "https://ailleurs.example.net/scan", null).url()).isNotNull();
    }
}
