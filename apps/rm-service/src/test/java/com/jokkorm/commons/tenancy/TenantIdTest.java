package com.jokkorm.commons.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantIdTest {

    @Test
    @DisplayName("of() accepte un slug valide en minuscules")
    void should_accept_valid_slug() {
        assertThat(TenantId.of("acme-corp").value()).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("of() accepte chiffres et tirets")
    void should_accept_digits_and_hyphens() {
        assertThat(TenantId.of("tenant-42").value()).isEqualTo("tenant-42");
    }

    @Test
    @DisplayName("of() rejette null")
    void should_reject_null() {
        assertThatThrownBy(() -> TenantId.of(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    @DisplayName("of() rejette les valeurs vides / blanches")
    void should_reject_blank(String blank) {
        assertThatThrownBy(() -> TenantId.of(blank))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Acme",
            "acme_corp",
            "acme corp",
            "acme.corp",
            "acme/corp",
            "-acme",
            "acme-",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        })
    @DisplayName("of() rejette les identifiants qui violent le pattern")
    void should_reject_invalid_pattern(String invalid) {
        assertThatThrownBy(() -> TenantId.of(invalid))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("égalité de valeur (sémantique record)")
    void should_have_value_equality() {
        assertThat(TenantId.of("acme")).isEqualTo(TenantId.of("acme"));
        assertThat(TenantId.of("acme")).isNotEqualTo(TenantId.of("other"));
    }
}
