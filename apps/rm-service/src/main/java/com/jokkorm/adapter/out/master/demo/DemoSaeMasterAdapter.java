package com.jokkorm.adapter.out.master.demo;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p>Activation : {@code jokkorm.master.kind=demo}.
 */
@Component
@ConditionalOnProperty(name = "jokkorm.master.kind", havingValue = "demo")
public class DemoSaeMasterAdapter implements SaeMasterPort {

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

    public DemoSaeMasterAdapter() {
        LOG.warn("DemoSaeMasterAdapter active — master data is SYNTHETIC. Never use this adapter "
            + "in production: show the demo banner in the UI when kind=demo.");
    }

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        String raw = cote.value();
        String firstSegment = firstSegment(raw);
        String lastSegment = lastSegment(raw);
        Prefix prefix = PREFIXES.getOrDefault(firstSegment.toUpperCase(Locale.ROOT), DEFAULT_PREFIX);

        String title = "%s n° %s".formatted(prefix.label, lastSegment);
        String classificationPath = classificationPath(raw, prefix);
        boolean legalHold = raw.toUpperCase(Locale.ROOT).matches(".*(GEL|LITIGE|HOLD).*");

        return Optional.of(new MasterArchiveView(
            cote, title, classificationPath, prefix.duaYears, prefix.finalFate, legalHold));
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
}
