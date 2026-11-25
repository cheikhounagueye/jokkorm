package com.jokkorm.application.record;

import com.jokkorm.application.master.MasterSnapshotService;
import com.jokkorm.application.master.MasterSnapshotService.Facts;
import com.jokkorm.application.record.RecordContainerConflictException.Reason;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterRecordSnapshot;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Règles de regroupement à la mise en boîte (étude du 24 novembre 2026, décision D2 : bloquantes).
 *
 * <ul>
 *   <li><b>R1</b> — un seul service producteur par boîte : respect des fonds, restitution et
 *       communication par service. Un service réorganisé garde ses anciennes boîtes : la
 *       comparaison porte sur le code tel que le maître l'écrit, sans table d'équivalence.</li>
 *   <li><b>R2</b> — un seul sort final par boîte : sinon elle ne peut être ni éliminée ni versée
 *       entière.</li>
 * </ul>
 *
 * <p>La référence est le contenu actuel de la boîte : rien n'est stocké sur la boîte. Une boîte vide
 * prend le service et le sort final de ses premiers dossiers. Seule la boîte d'arrivée est
 * contrôlée : sortir un dossier d'une boîte mélangée (transfert) reste toujours possible, c'est
 * ainsi qu'on la reconditionne.
 *
 * <p>Les données viennent de la copie locale du maître (D3) : une liaison coupée n'empêche pas de
 * ranger. On refuse quand on ne sait pas : maître muet sans copie, dossier inconnu du maître,
 * fiche sans service ou sans sort final, sort final que JokkoRM ne sait pas lire (la valeur est
 * nommée). Avant de refuser sur la foi de la copie, on relit les fiches concernées chez le maître :
 * une fiche corrigée à l'instant compte tout de suite.
 *
 * <p>Espace sans maître : ni service ni sort final connus, les règles sont inactives.
 */
@Service
public class BoxGroupingRules {

    private static final Logger LOG = LoggerFactory.getLogger(BoxGroupingRules.class);

    /** Une copie plus récente n'est pas relue : elle vient d'être lue. */
    static final Duration REREAD_OLDER_THAN = Duration.ofMinutes(1);
    /** Relectures au plus par contrôle : une liaison lente ne doit pas bloquer la boîte longtemps. */
    static final int MAX_REREADS = 20;

    /** Un manquement : {@code cote} null = la boîte ou la sélection entière. */
    public record Breach(String cote, Reason reason, String message) {}

    /** Service et sort final d'un dossier dont la fiche est complète et lisible. */
    private record Group(String serviceKey, String service, String fate) {}

    private final PhysicalArchiveRecordRepository records;
    private final MasterSnapshotService snapshots;
    private final Clock clock;

    public BoxGroupingRules(PhysicalArchiveRecordRepository records, MasterSnapshotService snapshots, Clock clock) {
        this.records = records;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    /**
     * Contrôle l'arrivée de {@code incoming} dans {@code box}. À appeler sous le verrou du contenu de
     * la boîte : le contenu lu est celui qui compte.
     *
     * @return les manquements, vide si les dossiers peuvent entrer
     */
    public List<Breach> check(TenantId tenant, PhysicalContainer box, List<PhysicalArchiveRecord> incoming) {
        if (incoming.isEmpty() || snapshots.standalone(tenant)) {
            return List.of();
        }
        Set<UUID> incomingIds = incoming.stream().map(PhysicalArchiveRecord::id).collect(Collectors.toSet());
        List<PhysicalArchiveRecord> contents = records.findByContainer(tenant, box.id()).stream()
            .filter(r -> !incomingIds.contains(r.id()))
            .toList();
        List<PhysicalArchiveRecord> all = Stream.concat(contents.stream(), incoming.stream()).toList();

        Map<UUID, Facts> facts = snapshots.factsFor(tenant, all);
        List<Breach> breaches = evaluate(box, contents, incoming, facts);
        if (breaches.isEmpty()) {
            return breaches;
        }
        if (rereadStale(tenant, breaches, contents, incoming, facts) > 0) {
            breaches = evaluate(box, contents, incoming, snapshots.factsFor(tenant, all));
        }
        return breaches;
    }

    /** Même contrôle pour un seul dossier ; lève l'exception de conflit (HTTP 409) au premier refus. */
    public void require(TenantId tenant, PhysicalContainer box, PhysicalArchiveRecord record,
                        BreachHandler onBreach) {
        List<Breach> breaches = check(tenant, box, List.of(record));
        if (!breaches.isEmpty()) {
            Breach first = breaches.get(0);
            String message = breaches.stream().map(Breach::message).distinct().collect(Collectors.joining(" "));
            throw onBreach.conflict(first.reason(), message);
        }
    }

    /** Fabrique l'exception de l'appelant (qui y joint la boîte et son adresse). */
    @FunctionalInterface
    public interface BreachHandler {
        RuntimeException conflict(Reason reason, String message);
    }

    // --- évaluation -------------------------------------------------------------------------

    private List<Breach> evaluate(PhysicalContainer box, List<PhysicalArchiveRecord> contents,
                                  List<PhysicalArchiveRecord> incoming, Map<UUID, Facts> facts) {
        List<Breach> out = new ArrayList<>();
        String boxName = name(box);

        // 1. Ce que la boîte contient déjà.
        List<String> contentUnknown = new ArrayList<>();
        Map<String, String> boxServices = new LinkedHashMap<>(); // clé → code affiché
        Set<String> boxFates = new LinkedHashSet<>();
        for (PhysicalArchiveRecord c : contents) {
            Group g = group(facts.get(c.id()));
            if (g == null) {
                contentUnknown.add(c.cote().value());
            } else {
                boxServices.putIfAbsent(g.serviceKey(), g.service());
                boxFates.add(g.fate());
            }
        }
        boolean boxProblem = false;
        if (!contentUnknown.isEmpty()) {
            boxProblem = true;
            out.add(new Breach(null, Reason.BOX_UNVERIFIABLE,
                ("La boîte %s contient %s dont la GED maître ne donne pas le service producteur ou le sort final "
                    + "(%s) : impossible de vérifier qu'un nouveau dossier lui convient. Complétez ces fiches dans "
                    + "la GED maître, ou sortez ces dossiers de la boîte.")
                    .formatted(boxName, count(contentUnknown.size(), "dossier"), list(contentUnknown))));
        }
        if (boxServices.size() > 1 || boxFates.size() > 1) {
            boxProblem = true;
            List<String> mixes = new ArrayList<>();
            if (boxServices.size() > 1) mixes.add("les services " + and(boxServices.values()));
            if (boxFates.size() > 1) mixes.add("les sorts finaux " + and(boxFates.stream().map(BoxGroupingRules::fateLabel).toList()));
            out.add(new Breach(null, Reason.BOX_MIXED,
                ("La boîte %s mélange déjà %s : elle ne reçoit plus de dossier. Reconditionnez-la d'abord en "
                    + "transférant les dossiers qui n'y ont pas leur place.")
                    .formatted(boxName, String.join(" et ", mixes))));
        }

        // 2. Les dossiers qui arrivent : fiche connue, complète et lisible.
        Map<UUID, Group> arriving = new LinkedHashMap<>();
        for (PhysicalArchiveRecord r : incoming) {
            String cote = r.cote().value();
            Facts f = facts.get(r.id());
            Breach missing = factsBreach(cote, f);
            if (missing != null) {
                out.add(missing);
            } else {
                arriving.put(r.id(), group(f));
            }
        }
        if (boxProblem || arriving.isEmpty()) {
            return out;
        }

        // 3. Comparaison avec la boîte, ou entre eux si la boîte est vide.
        if (!boxServices.isEmpty()) {
            String serviceKey = boxServices.keySet().iterator().next();
            String service = boxServices.get(serviceKey);
            String fate = boxFates.iterator().next();
            for (PhysicalArchiveRecord r : incoming) {
                Group g = arriving.get(r.id());
                if (g == null) continue;
                boolean otherService = !g.serviceKey().equals(serviceKey);
                boolean otherFate = !g.fate().equals(fate);
                String cote = r.cote().value();
                if (otherService) {
                    out.add(new Breach(cote, Reason.SERVICE_MISMATCH,
                        ("%s relève du service %s ; la boîte %s contient des dossiers du service %s. Une boîte ne "
                            + "contient qu'un service producteur : mettez %s dans une boîte du service %s.")
                            .formatted(cote, g.service(), boxName, service, cote, g.service())
                            + (otherFate ? " Son sort final diffère aussi (%s, la boîte : %s)."
                                .formatted(fateLabel(g.fate()), fateLabel(fate)) : "")));
                } else if (otherFate) {
                    out.add(new Breach(cote, Reason.FATE_MISMATCH,
                        ("%s a pour sort final %s ; la boîte %s contient des dossiers à %s. Une boîte n'a qu'un sort "
                            + "final, sinon elle ne peut être ni éliminée ni versée entière : mettez %s dans une "
                            + "boîte %s.")
                            .formatted(cote, fateLabel(g.fate()), boxName, fateLabel(fate), cote,
                                fateLabel(g.fate()) + " du service " + g.service())));
                }
            }
            return out;
        }
        Map<String, Integer> byService = new LinkedHashMap<>();
        Map<String, String> serviceShown = new LinkedHashMap<>();
        Map<String, Integer> byFate = new LinkedHashMap<>();
        for (Group g : arriving.values()) {
            byService.merge(g.serviceKey(), 1, Integer::sum);
            serviceShown.putIfAbsent(g.serviceKey(), g.service());
            byFate.merge(g.fate(), 1, Integer::sum);
        }
        if (byService.size() > 1) {
            out.add(new Breach(null, Reason.SELECTION_MIXED,
                ("Les dossiers choisis relèvent de %d services (%s) : une boîte n'en reçoit qu'un. Choisissez "
                    + "les dossiers d'un seul service.")
                    .formatted(byService.size(), tally(byService, serviceShown::get))));
        }
        if (byFate.size() > 1) {
            out.add(new Breach(null, Reason.SELECTION_MIXED,
                ("Les dossiers choisis ont %d sorts finaux (%s) : une boîte n'en a qu'un. Choisissez des "
                    + "dossiers de même sort final.")
                    .formatted(byFate.size(), tally(byFate, BoxGroupingRules::fateLabel))));
        }
        return out;
    }

    /** Le dossier qui arrive : pourquoi son service ou son sort final n'est pas utilisable, ou null. */
    private static Breach factsBreach(String cote, Facts f) {
        if (f instanceof Facts.Unreachable u) {
            return new Breach(cote, Reason.MASTER_FACTS_UNREACHABLE,
                ("%s : la GED maître ne répond pas et JokkoRM n'a pas encore lu sa fiche ; impossible de vérifier "
                    + "son service producteur et son sort final. Réessayez quand la liaison sera rétablie.")
                    .formatted(cote));
        }
        if (!(f instanceof Facts.Known known)) {
            return new Breach(cote, Reason.MASTER_FACTS_MISSING,
                ("%s est inconnu de la GED maître : sans sa fiche, impossible de vérifier son service producteur "
                    + "et son sort final.").formatted(cote));
        }
        MasterRecordSnapshot s = known.snapshot();
        boolean noService = s.service().isEmpty();
        boolean noFate = s.fate().isEmpty();
        if (!noService && !noFate) {
            return null;
        }
        String fix = " Complétez la fiche dans la GED maître, puis recommencez.";
        if (noFate && s.finalFateSource() != null) {
            return new Breach(cote, Reason.MASTER_FACTS_MISSING,
                ("Le sort final de %s dans la GED maître, « %s », n'est pas reconnu par JokkoRM : impossible de "
                    + "savoir avec quels dossiers le ranger. Signalez-le à l'administrateur de JokkoRM.")
                    .formatted(cote, s.finalFateSource())
                    + (noService ? " Sa fiche n'indique pas non plus de service producteur." : ""));
        }
        String what = noService && noFate ? "ni service producteur ni sort final"
            : noService ? "pas de service producteur" : "pas de sort final";
        return new Breach(cote, Reason.MASTER_FACTS_MISSING,
            "La fiche de %s dans la GED maître n'indique %s.".formatted(cote, what) + fix);
    }

    /** Service et sort final utilisables, ou null (fiche inconnue, incomplète ou illisible). */
    private static Group group(Facts f) {
        if (!(f instanceof Facts.Known known)) return null;
        MasterRecordSnapshot s = known.snapshot();
        if (s.service().isEmpty() || s.fate().isEmpty()) return null;
        String service = s.service().get();
        return new Group(service.toUpperCase(Locale.ROOT), service, s.fate().get().toUpperCase(Locale.ROOT));
    }

    // --- relecture avant refus ---------------------------------------------------------------

    /**
     * Relit chez le maître les fiches dont dépend le refus et dont la copie a plus d'une minute :
     * d'abord les dossiers refusés, puis les autres dossiers qui arrivent, puis le contenu de la boîte.
     *
     * @return nombre de fiches relues
     */
    private int rereadStale(TenantId tenant, List<Breach> breaches, List<PhysicalArchiveRecord> contents,
                            List<PhysicalArchiveRecord> incoming, Map<UUID, Facts> facts) {
        boolean onlyUnreachable = breaches.stream().allMatch(b -> b.reason() == Reason.MASTER_FACTS_UNREACHABLE);
        if (onlyUnreachable) return 0;
        Set<String> refused = new HashSet<>();
        breaches.forEach(b -> { if (b.cote() != null) refused.add(b.cote()); });
        List<PhysicalArchiveRecord> order = new ArrayList<>();
        incoming.stream().filter(r -> refused.contains(r.cote().value())).forEach(order::add);
        incoming.stream().filter(r -> !refused.contains(r.cote().value())).forEach(order::add);
        order.addAll(contents);

        Instant before = clock.instant().minus(REREAD_OLDER_THAN);
        int done = 0;
        for (PhysicalArchiveRecord r : order) {
            if (done >= MAX_REREADS) break;
            if (!(facts.get(r.id()) instanceof Facts.Known known) || !known.snapshot().fetchedAt().isBefore(before)) {
                continue;
            }
            try {
                if (snapshots.rereadNow(tenant, r.id(), r.cote()).isPresent()) done++;
            } catch (MasterUnreachableException e) {
                LOG.info("Relecture avant refus de mise en boîte interrompue (tenant {}) : {}", tenant.value(), e.getMessage());
                break;
            }
        }
        return done;
    }

    // --- mise en forme ----------------------------------------------------------------------

    static String fateLabel(String fate) {
        return switch (fate) {
            case "DESTRUCTION" -> "« destruction »";
            case "CONSERVATION" -> "« conservation »";
            case "VERSEMENT_AN" -> "« versement »";
            case "TRI" -> "« tri »";
            default -> "« " + fate.toLowerCase(Locale.ROOT) + " »";
        };
    }

    private static String name(PhysicalContainer box) {
        return box.label() == null || box.label().isBlank()
            ? box.barcode()
            : "%s « %s »".formatted(box.barcode(), box.label().strip());
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n > 1 ? "s" : "");
    }

    private static String and(java.util.Collection<String> items) {
        List<String> l = List.copyOf(items);
        if (l.size() <= 1) return String.join("", l);
        return String.join(", ", l.subList(0, l.size() - 1)) + " et " + l.get(l.size() - 1);
    }

    private static String list(List<String> cotes) {
        return cotes.size() <= 5 ? String.join(", ", cotes)
            : String.join(", ", cotes.subList(0, 5)) + "… (" + cotes.size() + " en tout)";
    }

    private static String tally(Map<String, Integer> counts, java.util.function.Function<String, String> label) {
        return counts.entrySet().stream()
            .map(e -> label.apply(e.getKey()) + " : " + e.getValue())
            .collect(Collectors.joining(" ; "));
    }
}
