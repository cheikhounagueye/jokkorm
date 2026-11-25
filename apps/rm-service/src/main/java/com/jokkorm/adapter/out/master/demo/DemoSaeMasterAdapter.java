package com.jokkorm.adapter.out.master.demo;

import com.jokkorm.adapter.out.master.MasterAdapter;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import com.jokkorm.domain.master.ArchiveSearchResult;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter « démo » — fabrique des données maître plausibles à partir de la cote elle-même,
 * sans serveur externe. Objectif : rendre le panneau {@code master} vivant dans l'UI
 * {@code /records/detail} pour la démonstration commerciale, avant d'avoir branché un
 * vrai Maarch RM ou équivalent.
 *
 * <p><b>Jamais en prod.</b> Les données sont inventées : titres génériques, DUA fixe,
 * sort final uniforme. Un bandeau d'avertissement devrait accompagner tout déploiement
 * en mode {@code demo} (les clients à qui on montre ça doivent savoir que ce n'est pas
 * leur vrai contenu maître).
 *
 * <p><b>Règles de génération</b> à partir de la cote :
 * <ul>
 *   <li><b>title</b> — déduit du premier segment : {@code DMG/MP/...} → « Marché public n° … »,
 *       {@code RH/CONTRAT/...} → « Contrat RH n° … », {@code FIN/...} → « Dossier financier n° … »,
 *       sinon « Dossier &lt;cote&gt; »</li>
 *   <li><b>classificationPath</b> — segments avant le dernier, en minuscules, séparés par {@code /}</li>
 *   <li><b>duaYears</b> — 10 par défaut, 30 pour les contrats RH, 90 pour les actes d'état civil</li>
 *   <li><b>finalFate</b> — {@code VERSEMENT_AN} par défaut, {@code CONSERVATION} pour les actes
 *       d'état civil, {@code DESTRUCTION} pour les factures routinières</li>
 *   <li><b>legalHold</b> — {@code true} si la cote contient {@code GEL}, {@code LITIGE} ou {@code HOLD}</li>
 * </ul>
 *
 * <p>Activation : {@code jokkorm.master.kind=demo} ou {@code jokkorm.master.tenants.<tenant>=demo}.
 * Le bean existe toujours ; seul le routage par tenant décide s'il sert (voir RoutingSaeMasterPort).
 */
@Component
public class DemoSaeMasterAdapter implements MasterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSaeMasterAdapter.class);

    /** Préfixe de cote → libellé lisible. Enrichir ici si on veut couvrir plus de cas. */
    private static final Map<String, Prefix> PREFIXES = Map.of(
        "DMG",   new Prefix("Marché public",            "direction des moyens généraux", 10, "VERSEMENT_AN"),
        "RH",    new Prefix("Contrat RH",               "ressources humaines",           30, "DESTRUCTION"),
        "FIN",   new Prefix("Dossier financier",        "finances et comptabilité",      10, "VERSEMENT_AN"),
        "FACT",  new Prefix("Facture",                  "comptabilité fournisseurs",     10, "DESTRUCTION"),
        "EC",    new Prefix("Acte d'état civil",        "état civil",                    90, "CONSERVATION"),
        "URBA",  new Prefix("Dossier urbanisme",        "urbanisme",                     30, "CONSERVATION"),
        "JUR",   new Prefix("Dossier juridique",        "direction juridique",           30, "VERSEMENT_AN"));

    private static final Prefix DEFAULT_PREFIX =
        new Prefix("Dossier",                 "non classé",                    10, "VERSEMENT_AN");

    @Override
    public String kind() { return "demo"; }

    @Override
    public String displayName() { return "Maître de démonstration"; }

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        String raw = cote.value();
        String firstSegment = firstSegment(raw);
        String lastSegment = lastSegment(raw);
        Prefix prefix = PREFIXES.getOrDefault(firstSegment.toUpperCase(Locale.ROOT), DEFAULT_PREFIX);

        String title = "%s n° %s".formatted(prefix.label, lastSegment);
        String classificationPath = classificationPath(raw, prefix);
        boolean legalHold = raw.toUpperCase(Locale.ROOT).matches(".*(GEL|LITIGE|HOLD).*");

        // Données de regroupement (D3) : service = premier segment connu, échéance = année de la
        // cote + DUA, classifié si la cote contient CONF. Inventé, mais assez varié pour montrer
        // les règles de mise en boîte (deux services, deux sorts, deux années).
        String service = PREFIXES.containsKey(firstSegment.toUpperCase(Locale.ROOT))
            ? firstSegment.toUpperCase(Locale.ROOT) : "DIVERS";
        LocalDate disposal = yearOf(raw).map(y -> LocalDate.of(y + prefix.duaYears, 12, 31)).orElse(null);
        String classification = raw.toUpperCase(Locale.ROOT).contains("CONF") ? "CONFIDENTIEL" : null;

        return Optional.of(new MasterArchiveView(
            cote, title, classificationPath, prefix.duaYears, prefix.finalFate, legalHold, null,
            service, disposal, classification, prefix.finalFate));
    }

    private static final java.util.regex.Pattern YEAR =
        java.util.regex.Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");

    private static Optional<Integer> yearOf(String cote) {
        var m = YEAR.matcher(cote);
        return m.find() ? Optional.of(Integer.parseInt(m.group())) : Optional.empty();
    }

    @Override
    public Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject) {
        // Demo : on renvoie un identité symbolique, pas d'annuaire.
        return Optional.of(new AgentIdentity(subject, "Demo · " + subject, java.util.List.of()));
    }

    @Override
    public void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location) {
        LOG.info("[demo] notifyPhysicalLocation tenant={} cote={} location={}",
            tenant.value(), cote.value(), location.toSingleLine());
    }

    @Override
    public void notifyMovement(TenantId tenant, Cote cote, MovementEvent event) {
        LOG.info("[demo] notifyMovement tenant={} cote={} kind={} agent={}",
            tenant.value(), cote.value(), event.kind(), event.agentSubject());
    }

    @Override
    public void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation) {
        LOG.info("[demo] notifyDestructionExecuted tenant={} cote={} attestation={} (pdf {} bytes)",
            tenant.value(), cote.value(), attestation.attestationId(), attestation.signedPdf().length);
    }

    // ───── helpers ─────────────────────────────────────────────────────────────

    private static String firstSegment(String cote) {
        int i = indexOfSeparator(cote);
        return i < 0 ? cote : cote.substring(0, i);
    }

    private static String lastSegment(String cote) {
        int i = Math.max(cote.lastIndexOf('/'), cote.lastIndexOf('-'));
        return i < 0 ? cote : cote.substring(i + 1);
    }

    private static int indexOfSeparator(String cote) {
        int slash = cote.indexOf('/');
        int dash = cote.indexOf('-');
        if (slash < 0) return dash;
        if (dash < 0) return slash;
        return Math.min(slash, dash);
    }

    private static String classificationPath(String cote, Prefix prefix) {
        int lastSep = Math.max(cote.lastIndexOf('/'), cote.lastIndexOf('-'));
        String head = lastSep < 0 ? cote : cote.substring(0, lastSep);
        // Format lisible : minuscules + "/" conservés
        return head.toLowerCase(Locale.ROOT)
            .replace('-', '/')
            + "  ·  " + prefix.direction;
    }

    private record Prefix(String label, String direction, int duaYears, String finalFate) {}

    /**
     * Phase 5Q — recherche métier simulée.
     * Génère des résultats plausibles selon les critères (vraies données en 5P.K/Maarch).
     */
    @Override
    public List<ArchiveSearchResult> search(TenantId tenant, ArchiveSearchCriteria c) {
        if (!c.hasAnyCriteria()) return List.of();

        List<ArchiveSearchResult> results = new ArrayList<>();
        int target = Math.min(c.limit(), 15); // par défaut on génère quelques résultats plausibles

        // Si une typologie est demandée, on génère des résultats cohérents
        String typo = c.typologieOpt().orElse(null);
        String service = c.serviceOpt().orElse(null);

        // Détermine préfixes candidats selon typologie/service
        List<String> prefixes = selectPrefixes(typo, service);

        // Génère N résultats fictifs mais plausibles
        var rnd = ThreadLocalRandom.current();
        String[] peopleNames = {"Awa Diop", "Mouhamadou Fall", "Fatou Ndiaye", "Cheikh Sarr",
            "Mariama Ba", "Ibrahima Sow", "Aïcha Diouf", "Omar Gueye"};
        String[] marketNames = {"Fourniture bureaux", "Entretien bâtiment", "Prestation IT",
            "Audit comptable", "Location véhicules"};

        for (int i = 0; i < target; i++) {
            String prefix = prefixes.get(i % prefixes.size());
            Prefix meta = PREFIXES.getOrDefault(prefix, DEFAULT_PREFIX);
            int year = yearInRange(c.dateFromOpt().orElse(null), c.dateToOpt().orElse(null));
            String num = String.format("%03d", i + 47);
            String cote = prefix + "/" + year + "/" + num;
            String title = switch (prefix) {
                case "RH" -> "Dossier RH — " + peopleNames[i % peopleNames.length];
                case "DMG" -> "Marché public — " + marketNames[i % marketNames.length] + " " + year;
                case "FIN" -> "Dossier financier " + year + " n°" + num;
                case "FACT" -> "Factures fournisseurs " + year + " — lot " + num;
                case "EC" -> "Acte d'état civil " + year + " n°" + num;
                case "JUR" -> "Dossier juridique " + year + " n°" + num;
                case "URBA" -> "Permis urbanisme " + year + " n°" + num;
                default -> "Dossier " + cote;
            };

            // Filtrage freeText (contains insensible à la casse)
            String freeText = c.freeTextOpt().orElse(null);
            if (freeText != null && !containsIgnoreCase(title, freeText) && !containsIgnoreCase(cote, freeText)) {
                continue;
            }

            LocalDate dateStart = LocalDate.of(year, 1, 1);
            LocalDate dateEnd = LocalDate.of(year, 12, 31);
            int age = Year.now().getValue() - year;
            int duaRemaining = Math.max(0, meta.duaYears - age);

            results.add(new ArchiveSearchResult(
                cote, title, meta.direction, prefix, classificationPath(cote, meta),
                dateStart, dateEnd, duaRemaining, true));
        }

        // Si cote explicitement demandée, on la met en premier résultat
        c.coteOpt().ifPresent(explicit -> {
            Prefix meta = PREFIXES.getOrDefault(firstSegment(explicit), DEFAULT_PREFIX);
            results.add(0, new ArchiveSearchResult(
                explicit, "Dossier " + explicit, meta.direction, firstSegment(explicit),
                classificationPath(explicit, meta), null, null, meta.duaYears, true));
        });

        LOG.info("[demo] search tenant={} criteria={} → {} results",
            tenant.value(), c, results.size());
        return results.stream().limit(c.limit()).toList();
    }

    private static List<String> selectPrefixes(String typo, String service) {
        if (typo != null) {
            // typologie = préfixe de cote
            String key = typo.toUpperCase(Locale.ROOT);
            if (PREFIXES.containsKey(key)) return List.of(key);
        }
        if (service != null) {
            String s = service.toLowerCase(Locale.ROOT);
            if (s.contains("rh") || s.contains("humain") || s.contains("personnel")) return List.of("RH");
            if (s.contains("fin") || s.contains("compta")) return List.of("FIN", "FACT");
            if (s.contains("juridi") || s.contains("legal")) return List.of("JUR");
            if (s.contains("civil") || s.contains("état civil")) return List.of("EC");
            if (s.contains("urba")) return List.of("URBA");
            if (s.contains("march") || s.contains("achat")) return List.of("DMG");
        }
        return new ArrayList<>(PREFIXES.keySet());
    }

    private static int yearInRange(LocalDate from, LocalDate to) {
        int min = from != null ? from.getYear() : 2015;
        int max = to != null ? to.getYear() : 2024;
        if (min > max) { int t = min; min = max; max = t; }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
