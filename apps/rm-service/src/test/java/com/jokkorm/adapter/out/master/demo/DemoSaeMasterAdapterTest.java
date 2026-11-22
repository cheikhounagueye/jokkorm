package com.jokkorm.adapter.out.master.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.record.Cote;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DemoSaeMasterAdapterTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private final DemoSaeMasterAdapter adapter = new DemoSaeMasterAdapter();

    @Test
    @DisplayName("fetchByCote renvoie toujours une vue (jamais empty) — essentiel pour la démo")
    void fetch_by_cote_always_returns_a_view() {
        Optional<MasterArchiveView> view = adapter.fetchByCote(TENANT, Cote.of("DMG/MP/2024/047"));
        assertThat(view).isPresent();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        # cote,                      expectedTitle,                           expectedDua,  expectedFate
        DMG/MP/2024/047,             'Marché public n° 047',                  10,            VERSEMENT_AN
        RH/CONTRAT/2024/042,         'Contrat RH n° 042',                     30,            DESTRUCTION
        FIN/BUDGET/2024,             'Dossier financier n° 2024',             10,            VERSEMENT_AN
        FACT/2024/001,               'Facture n° 001',                        10,            DESTRUCTION
        EC/NAISSANCE/2024/15,        'Acte d état civil n° 15',               90,            CONSERVATION
        URBA/PERMIS/2024/88,         'Dossier urbanisme n° 88',               30,            CONSERVATION
        JUR/CONTENTIEUX/2024/5,      'Dossier juridique n° 5',                30,            VERSEMENT_AN
        UNKNOWN/2024/9,              'Dossier n° 9',                          10,            VERSEMENT_AN
        """)
    @DisplayName("mapping préfixe → titre/DUA/sort final cohérent")
    void prefix_maps_to_realistic_metadata(
        String coteStr, String expectedTitle, int expectedDua, String expectedFate) {
        // Petite accommodation : le CSV ne gère pas les apostrophes françaises
        String title = expectedTitle.replace("état civil", "état civil").replace("d état", "d'état");
        MasterArchiveView view = adapter.fetchByCote(TENANT, Cote.of(coteStr)).orElseThrow();
        assertThat(view.title()).isEqualTo(title);
        assertThat(view.duaYears()).isEqualTo(expectedDua);
        assertThat(view.finalFate()).isEqualTo(expectedFate);
    }

    @ParameterizedTest
    @CsvSource({
        "DMG/MP/GEL/2024/1",
        "RH/CONTRAT/LITIGE/042",
        "FIN/HOLD/2024"
    })
    @DisplayName("legalHold vrai si la cote contient GEL / LITIGE / HOLD")
    void detects_legal_hold_marker(String coteStr) {
        MasterArchiveView view = adapter.fetchByCote(TENANT, Cote.of(coteStr)).orElseThrow();
        assertThat(view.legalHold()).isTrue();
    }

    @Test
    @DisplayName("legalHold faux par défaut pour une cote classique")
    void legal_hold_false_when_no_marker() {
        MasterArchiveView view = adapter.fetchByCote(TENANT, Cote.of("DMG/MP/2024/047")).orElseThrow();
        assertThat(view.legalHold()).isFalse();
    }

    @Test
    @DisplayName("resolveAgent renvoie une identité symbolique en démo")
    void resolve_agent_symbolic() {
        var identity = adapter.resolveAgent(TENANT, "khady.diouf").orElseThrow();
        assertThat(identity.subject()).isEqualTo("khady.diouf");
        assertThat(identity.displayName()).contains("khady.diouf");
    }
}
