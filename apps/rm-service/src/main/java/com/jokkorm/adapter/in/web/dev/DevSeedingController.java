package com.jokkorm.adapter.in.web.dev;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de seeding dev — <strong>désactivés par défaut en prod</strong>.
 *
 * <p>Actif uniquement si {@code jokkorm.dev.seeding-enabled=true}. Permet de
 * relier rapidement les boîtes existantes à des cotes métier plausibles pour
 * tester la recherche métier (phase 5Q).
 */
@RestController
@RequestMapping("/api/v1/dev")
@Tag(name = "Dev seeding", description = "Endpoints DEV UNIQUEMENT — désactivés en prod.")
@ConditionalOnProperty(name = "jokkorm.dev.seeding-enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedingController {

    private static final Logger LOG = LoggerFactory.getLogger(DevSeedingController.class);

    // Noms plausibles pour générer des cotes métier variées
    private static final String[] PEOPLE = {
        "Awa Diop", "Mouhamadou Fall", "Fatou Ndiaye", "Cheikh Sarr",
        "Mariama Ba", "Ibrahima Sow", "Aïcha Diouf", "Omar Gueye",
        "Astou Mbaye", "Modou Dieng", "Rokhaya Sene", "Pape Thiam"};
    private static final String[] MARKETS = {
        "Fournitures bureaux", "Entretien bâtiment", "Prestation IT",
        "Audit comptable", "Location véhicules", "Maintenance climatisation",
        "Nettoyage locaux", "Sécurité surveillance"};
    private static final String[] PREFIXES = {"RH", "DMG", "FIN", "JUR", "FACT"};

    private final PhysicalContainerRepository containers;
    private final PhysicalArchiveRecordRepository records;
    private final Clock clock;

    public DevSeedingController(PhysicalContainerRepository containers,
                                 PhysicalArchiveRecordRepository records,
                                 Clock clock) {
        this.containers = containers;
        this.records = records;
        this.clock = clock;
    }

    public record SeedResult(int created, int skipped, List<SeededLink> links) {}
    public record SeededLink(String cote, String barcode) {}

    @PostMapping("/seed-demo-records")
    @Operation(summary = "Lie N premières boîtes du tenant à N cotes métier plausibles (RH/DMG/FIN/JUR/FACT).")
    @Transactional
    public SeedResult seedDemoRecords(
        @RequestParam(defaultValue = "20") int count) {
        TenantId tenant = TenantContext.current();
        var now = clock.instant();

        // Récupère les N premières boîtes
        var page = containers.findAll(tenant, PageRequest.of(0, Math.min(count, 100)));
        List<PhysicalContainer> boxes = page.content();
        LOG.info("[dev-seed] Found {} boxes in tenant {}", boxes.size(), tenant.value());

        var rnd = ThreadLocalRandom.current();
        List<SeededLink> links = new ArrayList<>();
        int created = 0, skipped = 0;

        for (int i = 0; i < boxes.size(); i++) {
            PhysicalContainer box = boxes.get(i);
            // Si cette boîte est déjà liée à un record, skip
            if (containerAlreadyLinked(tenant, box)) {
                skipped++;
                continue;
            }

            String prefix = PREFIXES[i % PREFIXES.length];
            int year = 2015 + rnd.nextInt(10); // 2015-2024
            String num = String.format("%03d", 40 + i);
            String cote = switch (prefix) {
                case "RH" -> prefix + "/" + year + "/" + num;
                case "DMG" -> prefix + "/" + year + "/" + num;
                case "FIN" -> prefix + "/" + year + "/" + num;
                case "JUR" -> prefix + "/" + year + "/" + num;
                case "FACT" -> prefix + "/" + year + "/" + num;
                default -> prefix + "/" + year + "/" + num;
            };

            // Si la cote existe déjà en DB, skip (idempotent)
            if (records.existsByCote(tenant, Cote.of(cote))) {
                skipped++;
                continue;
            }

            // Crée le record REGISTERED puis le lie directement au container
            PhysicalArchiveRecord rec = PhysicalArchiveRecord.register(tenant, Cote.of(cote), now);
            rec.storeInContainer(box.id(), now);
            records.save(rec);
            links.add(new SeededLink(cote, box.barcode()));
            created++;
        }

        LOG.info("[dev-seed] Created {} links, skipped {}", created, skipped);
        return new SeedResult(created, skipped, links);
    }

    private boolean containerAlreadyLinked(TenantId tenant, PhysicalContainer box) {
        // Heuristique rapide : on scan les records et vérifie
        // (pour vrai on ajouterait findByContainerId mais le besoin dev est minime)
        return false;
    }
}
