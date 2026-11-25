package com.jokkorm.application.record;

import com.jokkorm.application.physical.PhysicalContainerNotFoundException;
import com.jokkorm.application.record.ConditioningRefusedException.Refusal;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conditionnement : mettre plusieurs dossiers « À conditionner » dans une même boîte, d'un geste.
 *
 * <p>Tout ou rien : chaque dossier et la boîte sont vérifiés d'abord, sous verrou du contenu de
 * la boîte ; au moindre refus rien n'est écrit et l'écran reçoit la raison de chaque refus.
 * Sinon chaque dossier passe par {@link StoreRecordInContainerUseCase} (même règles, une entrée
 * d'audit et une ligne de journal par dossier), dans la même transaction.
 *
 * <p>Règles de regroupement R1 et R2 ({@link BoxGroupingRules}) : les dossiers choisis ont le service
 * producteur et le sort final des dossiers déjà dans la boîte, ou, si elle est vide, les mêmes entre eux.
 *
 * <p>La boîte peut être en zone tampon (D1) : on conditionne avant de placer. Pas de transfert
 * ici : un dossier déjà dans une boîte en change par le geste de transfert, un par un, confirmé.
 */
@Service
public class ConditionRecordsUseCase {

    /** Au-delà, on découpe : une boîte d'archives ne contient jamais autant de dossiers. */
    public static final int MAX_PER_CALL = 200;

    public record Result(PhysicalContainer container, List<PhysicalArchiveRecord> stored, long recordCount) {}

    private final PhysicalArchiveRecordRepository records;
    private final PhysicalContainerRepository containers;
    private final PhysicalLoanRepository loans;
    private final StoreRecordInContainerUseCase store;
    private final BoxGroupingRules grouping;

    public ConditionRecordsUseCase(
        PhysicalArchiveRecordRepository records,
        PhysicalContainerRepository containers,
        PhysicalLoanRepository loans,
        StoreRecordInContainerUseCase store,
        BoxGroupingRules grouping) {
        this.grouping = grouping;
        this.records = records;
        this.containers = containers;
        this.loans = loans;
        this.store = store;
    }

    /** À conditionner : dans aucune boîte, enregistrés ou gelés, les plus anciens d'abord. */
    @Transactional(readOnly = true)
    public com.jokkorm.domain.common.Page<PhysicalArchiveRecord> pending(com.jokkorm.domain.common.PageRequest page) {
        return records.findToCondition(TenantContext.current(), page);
    }

    @Transactional
    public Result execute(String containerBarcode, List<String> rawCotes, String actor) {
        TenantId tenant = TenantContext.current();
        String barcode = containerBarcode == null ? "" : containerBarcode.trim().toUpperCase(Locale.ROOT);
        Set<String> wanted = new LinkedHashSet<>();
        for (String raw : rawCotes == null ? List.<String>of() : rawCotes) {
            if (raw != null && !raw.isBlank()) wanted.add(raw.trim());
        }
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Aucun dossier à mettre en boîte");
        }
        if (wanted.size() > MAX_PER_CALL) {
            throw new IllegalArgumentException(
                "Trop de dossiers d'un coup (%d) : %d au plus".formatted(wanted.size(), MAX_PER_CALL));
        }

        PhysicalContainer found = containers.findByBarcode(tenant, barcode)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Aucune boîte ne porte le code %s dans cet espace".formatted(barcode)));
        containers.lockContent(tenant, found.id());
        PhysicalContainer box = containers.findById(tenant, found.id()).orElse(found);
        String boxName = name(box);

        List<Refusal> refusals = new ArrayList<>();
        if (box.state() == ContainerState.DESTROYED || box.state() == ContainerState.SEALED) {
            refusals.add(new Refusal(null, "container-closed",
                "La boîte %s est %s : elle ne reçoit plus de dossier."
                    .formatted(boxName, box.state() == ContainerState.DESTROYED ? "détruite" : "fermée")));
        }
        loans.findActiveByContainer(tenant, box.id()).ifPresent(loan -> refusals.add(new Refusal(null,
            "container-on-loan",
            "La boîte %s %s : son contenu est promis tel quel."
                .formatted(boxName, loan.status().isActive() ? "est sortie en prêt" : "a une demande de prêt en cours"))));

        List<Cote> accepted = new ArrayList<>();
        List<PhysicalArchiveRecord> acceptedRecords = new ArrayList<>();
        for (String raw : wanted) {
            Cote cote;
            try {
                cote = Cote.of(raw);
            } catch (IllegalArgumentException e) {
                refusals.add(new Refusal(raw, "invalid-cote", "Cote invalide : " + raw));
                continue;
            }
            Optional<PhysicalArchiveRecord> record = records.findByCote(tenant, cote);
            if (record.isEmpty()) {
                refusals.add(new Refusal(raw, "record-not-found", "Aucun dossier enregistré sous la cote " + raw));
                continue;
            }
            Refusal refusal = refusalFor(tenant, record.get(), box, raw);
            if (refusal != null) {
                refusals.add(refusal);
            } else {
                accepted.add(cote);
                acceptedRecords.add(record.get());
            }
        }

        // Règles R1 et R2 : un service producteur et un sort final par boîte, et entre les dossiers choisis.
        for (BoxGroupingRules.Breach b : grouping.check(tenant, box, acceptedRecords)) {
            refusals.add(new Refusal(b.cote(), b.reason().slug(), b.message()));
        }

        long inside = records.countByContainer(tenant, box.id());
        box.capacity().ifPresent(capacity -> {
            long room = Math.max(0, capacity - inside);
            if (accepted.size() > room) {
                refusals.add(new Refusal(null, "container-full",
                    "La boîte %s contient %d dossiers sur %d : il reste %d place%s pour %d dossier%s."
                        .formatted(boxName, inside, capacity, room, room > 1 ? "s" : "",
                            accepted.size(), accepted.size() > 1 ? "s" : "")));
            }
        });

        if (!refusals.isEmpty()) {
            throw new ConditioningRefusedException(
                "Rien n'a été mis en boîte : %d refus. Corrige ou retire ces dossiers, puis recommence."
                    .formatted(refusals.size()),
                refusals);
        }

        List<PhysicalArchiveRecord> stored = new ArrayList<>();
        for (Cote cote : accepted) {
            stored.add(store.execute(cote, box.barcode(), false, actor));
        }
        PhysicalContainer after = containers.findById(tenant, box.id()).orElse(box);
        return new Result(after, stored, records.countByContainer(tenant, box.id()));
    }

    private Refusal refusalFor(TenantId tenant, PhysicalArchiveRecord record, PhysicalContainer box, String raw) {
        if (record.containerId().isPresent()) {
            if (record.containerId().get().equals(box.id())) {
                return new Refusal(raw, "record-already-in-container", "%s est déjà dans cette boîte".formatted(raw));
            }
            String other = record.containerId().flatMap(id -> containers.findById(tenant, id))
                .map(ConditionRecordsUseCase::name).orElse("une autre boîte");
            return new Refusal(raw, "record-in-other-container",
                "%s est déjà dans la boîte %s : pour l'en sortir, utilise le transfert".formatted(raw, other));
        }
        return switch (record.status()) {
            case FROZEN -> new Refusal(raw, "legal-hold",
                "%s est sous gel contentieux : il attend la levée du gel dans la GED maître".formatted(raw));
            case DESTROYED -> new Refusal(raw, "record-destroyed", "%s a été détruit".formatted(raw));
            case TRANSFERRED -> new Refusal(raw, "record-transferred", "%s a été versé définitivement".formatted(raw));
            case REGISTERED, STORED -> null;
        };
    }

    private static String name(PhysicalContainer box) {
        return box.label() == null || box.label().isBlank()
            ? box.barcode()
            : "%s « %s »".formatted(box.barcode(), box.label().strip());
    }
}
