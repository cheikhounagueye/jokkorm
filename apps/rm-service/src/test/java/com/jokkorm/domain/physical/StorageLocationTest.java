package com.jokkorm.domain.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageLocationTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Test
    @DisplayName("createSite : path = code, kind = SITE, pas de parent")
    void create_site() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", "Dakar centre", NOW);
        assertThat(site.kind()).isEqualTo(StorageLocationKind.SITE);
        assertThat(site.code()).isEqualTo("DKR");
        assertThat(site.path()).isEqualTo("DKR");
        assertThat(site.parentId()).isEmpty();
    }

    @Test
    @DisplayName("code est normalisé en majuscules")
    void code_normalization() {
        StorageLocation site = StorageLocation.createSite(TENANT, "dkr-nord", null, NOW);
        assertThat(site.code()).isEqualTo("DKR-NORD");
        assertThat(site.path()).isEqualTo("DKR-NORD");
    }

    @Test
    @DisplayName("createChild : path dérivé, parent correct")
    void create_child_computes_path() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", "Dakar", NOW);
        StorageLocation storehouse = StorageLocation.createChild(
            site, StorageLocationKind.STOREHOUSE, "MAG-B", "Magasin B", NOW);

        assertThat(storehouse.kind()).isEqualTo(StorageLocationKind.STOREHOUSE);
        assertThat(storehouse.path()).isEqualTo("DKR/MAG-B");
        assertThat(storehouse.parentId()).contains(site.id());
    }

    @Test
    @DisplayName("hiérarchie complète : SITE/STOREHOUSE/SHELF/RACK/POSITION")
    void full_depth_hierarchy() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        StorageLocation storehouse = StorageLocation.createChild(site, StorageLocationKind.STOREHOUSE, "MAG-B", null, NOW);
        StorageLocation shelf = StorageLocation.createChild(storehouse, StorageLocationKind.SHELF, "14", null, NOW);
        StorageLocation rack = StorageLocation.createChild(shelf, StorageLocationKind.RACK, "3", null, NOW);
        StorageLocation position = StorageLocation.createChild(rack, StorageLocationKind.POSITION, "07", null, NOW);

        assertThat(position.path()).isEqualTo("DKR/MAG-B/14/3/07");
    }

    @Test
    @DisplayName("createChild rejette un mauvais kind d'enfant")
    void create_child_rejects_wrong_kind() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        assertThatThrownBy(() -> StorageLocation.createChild(site, StorageLocationKind.RACK, "x", null, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Child of SITE must be STOREHOUSE");
    }

    @Test
    @DisplayName("code rejette caractères invalides")
    void code_rejects_invalid_charset() {
        assertThatThrownBy(() -> StorageLocation.createSite(TENANT, "dkr nord", null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageLocation.createSite(TENANT, "dkr/x", null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("code rejette blanc / null")
    void code_rejects_blank() {
        assertThatThrownBy(() -> StorageLocation.createSite(TENANT, "", null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageLocation.createSite(TENANT, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rename change le label et updatedAt")
    void rename_updates_label_and_timestamp() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", "Old", NOW);
        Instant later = NOW.plusSeconds(60);
        site.rename("New", later);
        assertThat(site.label()).isEqualTo("New");
        assertThat(site.updatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("StorageLocationKind — childKind et parentKind cohérents")
    void kind_hierarchy_bijection() {
        assertThat(StorageLocationKind.SITE.childKind()).isEqualTo(StorageLocationKind.STOREHOUSE);
        assertThat(StorageLocationKind.STOREHOUSE.parentKind()).isEqualTo(StorageLocationKind.SITE);
        assertThat(StorageLocationKind.POSITION.childKind()).isNull();
        assertThat(StorageLocationKind.SITE.parentKind()).isNull();
    }
}
