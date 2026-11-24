package com.jokkorm.application.physical;

import com.jokkorm.application.master.MasterSyncService;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Règle « un libellé, une seule boîte » : dans un espace, deux boîtes non détruites ne
 * portent jamais le même libellé, et deux lots jamais le même libellé commun (sinon
 * « Test gel · 001/001 » désignerait deux boîtes sur la tablette). Le code-barres reste
 * l'identifiant technique ; le libellé est ce que lit le magasinier, il doit être fiable.
 *
 * <ul>
 *   <li>La comparaison ignore la casse et les espaces en trop (« test  GEL » = « Test gel »).</li>
 *   <li>Une boîte détruite libère son libellé.</li>
 *   <li>Une boîte sans libellé n'est pas concernée (elle se lit par son code-barres).</li>
 *   <li>Les doublons antérieurs à la règle restent en place et se rangent normalement.</li>
 * </ul>
 * Chaque contrôle prend d'abord un verrou de transaction par espace : deux créations
 * simultanées du même libellé ne peuvent pas passer toutes les deux.
 */
@Service
public class ContainerLabelGuard {

    /** Séparateur ajouté par un lot entre le libellé commun et le numéro (« X · 001/020 »). */
    public static final String SERIES_SEPARATOR = " · ";

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final MasterSyncService addresses;

    public ContainerLabelGuard(PhysicalContainerRepository containers,
                               StorageLocationRepository locations,
                               MasterSyncService addresses) {
        this.containers = containers;
        this.locations = locations;
        this.addresses = addresses;
    }

    /** Libellé tel qu'il sera enregistré : espaces en trop retirés, {@code null} si vide. */
    public static String clean(String label) {
        if (label == null) return null;
        String s = label.strip().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Une boîte seule : refuse un libellé déjà porté par une boîte de l'espace. */
    public void requireFree(TenantId tenant, String label) {
        String wanted = clean(label);
        if (wanted == null) return;
        containers.lockLabels(tenant);
        List<PhysicalContainer> taken = containers.findActiveByLabel(tenant, wanted, 1);
        if (taken.isEmpty()) return;
        PhysicalContainer other = taken.get(0);
        String where = whereIs(tenant, other);
        throw new ContainerLabelTakenException(
            "Une boîte porte déjà le libellé « %s » : %s, %s. Deux boîtes ne peuvent pas porter "
                .formatted(other.label().strip(), other.barcode(), where)
                + "le même libellé : choisissez-en un autre, par exemple en ajoutant l'année ou un numéro.",
            other.barcode(), other.label().strip(), where, 1);
    }

    /** Un lot : refuse un libellé commun déjà utilisé par une autre série de boîtes. */
    public void requireFreeSeries(TenantId tenant, String labelPattern) {
        String wanted = clean(labelPattern);
        if (wanted == null) return;
        containers.lockLabels(tenant);
        String prefix = wanted + SERIES_SEPARATOR;
        List<PhysicalContainer> taken = containers.findActiveByLabelPrefix(tenant, prefix, 1);
        if (taken.isEmpty()) return;
        long count = containers.countActiveByLabelPrefix(tenant, prefix);
        PhysicalContainer other = taken.get(0);
        String where = whereIs(tenant, other);
        String boxes = count > 1 ? "%d boîtes, dont « %s »".formatted(count, other.label().strip())
                                 : "la boîte « %s »".formatted(other.label().strip());
        throw new ContainerLabelTakenException(
            "Le libellé commun « %s » est déjà celui d'une autre série : %s (%s, %s). "
                .formatted(wanted, boxes, other.barcode(), where)
                + "Deux lots ne peuvent pas porter le même libellé commun : choisissez-en un autre, "
                + "par exemple en ajoutant l'année ou un numéro de lot.",
            other.barcode(), other.label().strip(), where, count);
    }

    private String whereIs(TenantId tenant, PhysicalContainer box) {
        return box.positionId()
            .flatMap(id -> locations.findById(tenant, id))
            .map(p -> "rangée à " + addresses.address(tenant, p))
            .orElse("en zone tampon, pas encore rangée");
    }
}
