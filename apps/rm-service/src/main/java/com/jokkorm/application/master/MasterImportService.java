package com.jokkorm.application.master;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.record.HandleCoteAssignedEventUseCase;
import com.jokkorm.application.record.ImportFromMasterUseCase;
import com.jokkorm.application.record.RecordNotFoundException;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.master.MasterArchiveRef;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterEvent;
import com.jokkorm.domain.master.MasterImportCandidate;
import com.jokkorm.domain.master.MasterImportCandidate.Status;
import com.jokkorm.domain.master.MasterImportCandidateRepository;
import com.jokkorm.domain.master.MasterImportProfile;
import com.jokkorm.domain.master.MasterImportProfileRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liste « à importer » et import automatique par profil (étape 2, règle n° 4).
 *
 * <p>Chaque dépôt lu dans le journal du maître devient une ligne : à décider par l'archiviste
 * (importer s'il y a du papier, sinon ignorer), ou importée seule si son profil a la règle
 * « toujours du papier », décidée et datée par le responsable des archives. Ne deviennent jamais
 * candidates : une sous-archive (elle suit son dossier) et une archive déjà suivie.
 *
 * <p>Données personnelles : seuls l'identifiant, la cote, le profil, qui et quand sont gardés. Le
 * titre est lu en direct chez le maître pour l'écran, jamais stocké.
 */
@Service
public class MasterImportService {

    private static final Logger LOG = LoggerFactory.getLogger(MasterImportService.class);
    private static final Pattern PROFILE = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");
    /** Titres lus en direct chez le maître, au plus, par affichage de la liste. */
    static final int LIVE_TITLES = 30;
    static final int MAX_CATCH_UP_DAYS = 365;

    /** Ce qu'est devenu un dépôt lu dans le journal. */
    public enum Outcome {
        /** Nouvelle ligne à décider. */
        PENDING,
        /** Importée seule (règle du profil). */
        AUTO_IMPORTED,
        /** Le maître ne donne pas la cote au compte de JokkoRM. */
        UNREADABLE,
        /** Déjà suivie par JokkoRM. */
        ALREADY_TRACKED,
        /** Sous-archive : elle suit son dossier. */
        SUB_ARCHIVE,
        /** Dépôt déjà traité (événement relu, doublon du journal). */
        ALREADY_SEEN
    }

    public record Item(MasterImportCandidate candidate, String title, String masterPageUrl, boolean titleUnavailable) {
    }

    public record ProfileView(String profileReference, boolean autoImport, String decidedBy, Instant decidedAt,
                              int pending) {
    }

    public record Summary(boolean available, int pending, int unreadable, List<ProfileView> profiles) {
    }

    public record Bulk(int imported, int failed, String firstError) {
    }

    public record CatchUp(int days, int read, Map<Outcome, Integer> outcomes) {
    }

    private final SaeMasterPort master;
    private final MasterImportCandidateRepository candidates;
    private final MasterImportProfileRepository profiles;
    private final PhysicalArchiveRecordRepository records;
    private final HandleCoteAssignedEventUseCase register;
    private final ImportFromMasterUseCase importFromMaster;
    private final AuditChainService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MasterImportService(SaeMasterPort master, MasterImportCandidateRepository candidates,
                               MasterImportProfileRepository profiles, PhysicalArchiveRecordRepository records,
                               HandleCoteAssignedEventUseCase register, ImportFromMasterUseCase importFromMaster,
                               AuditChainService audit, TransactionTemplate tx, Clock clock) {
        this.master = master;
        this.candidates = candidates;
        this.profiles = profiles;
        this.records = records;
        this.register = register;
        this.importFromMaster = importFromMaster;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    // ─── Dépôt lu dans le journal (veilleur, rattrapage) ─────────────────────────

    /**
     * À appeler dans une transaction, tenant lié ; {@code ref} a été lu chez le maître AVANT (hors
     * transaction), {@code null} si le maître ne donne pas la cote. Rejouable : la ligne unique par
     * archive du maître est le marqueur « déjà traité ».
     */
    public Outcome onDeposited(TenantId tenant, MasterEvent ev, MasterArchiveRef ref) {
        Instant now = clock.instant();
        if (ref == null) {
            return candidates.insertIfAbsent(MasterImportCandidate.unreadable(tenant, ev, now))
                ? Outcome.UNREADABLE : Outcome.ALREADY_SEEN;
        }
        if (ref.child()) return Outcome.SUB_ARCHIVE;
        Cote cote = Cote.of(ref.cote());
        if (records.findByCote(tenant, cote).isPresent()) return Outcome.ALREADY_TRACKED;
        candidates.deleteUnreadable(tenant, ev.masterArchiveId());  // lisible maintenant : on la réévalue
        String profile = ref.profileReference();
        MasterImportProfile rule = profile == null ? null
            : profiles.find(tenant, profile).filter(MasterImportProfile::autoImport).orElse(null);
        if (rule == null) {
            return candidates.insertIfAbsent(MasterImportCandidate.pending(tenant, ev, cote.value(), profile, now))
                ? Outcome.PENDING : Outcome.ALREADY_SEEN;
        }
        if (!candidates.insertIfAbsent(MasterImportCandidate.autoImported(tenant, ev, cote.value(), profile,
            MasterWatchService.ACTOR, now))) {
            return Outcome.ALREADY_SEEN;
        }
        PhysicalArchiveRecord record = register.execute(tenant, cote, MasterWatchService.ACTOR);
        // La règle écrite et datée accompagne chaque import qu'elle déclenche (règle n° 4).
        audit.record(AuditAction.MASTER_DEPOSIT_AUTO_IMPORTED, AuditTargetType.PHYSICAL_ARCHIVE_RECORD, record.id(),
            MasterWatchService.ACTOR, "cote=" + cote.value() + "; profil=" + profile + "; regle-par=" + rule.decidedBy()
                + "; regle-le=" + rule.decidedAt() + "; evenement-maitre=" + ev.id() + "; depose-par=" + ev.actor());
        LOG.info("[import] {} : {} importée automatiquement (profil {})", tenant.value(), cote.value(), profile);
        return Outcome.AUTO_IMPORTED;
    }

    /**
     * Relit les dépôts des {@code days} derniers jours (premier branchement, droits corrigés…). Sans
     * effet sur le curseur du veilleur ; un dépôt déjà traité n'est pas rejoué.
     */
    public CatchUp catchUp(int days) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        if (days < 1 || days > MAX_CATCH_UP_DAYS) {
            throw new IllegalArgumentException("Rattrapage : entre 1 et " + MAX_CATCH_UP_DAYS + " jours");
        }
        TenantId tenant = TenantContext.current();
        requireJournal(tenant);
        List<MasterEvent> events = master.readEvents(tenant, clock.instant().minus(Duration.ofDays(days)),
            EnumSet.of(MasterEvent.Kind.DEPOSITED));
        Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        for (MasterEvent ev : events) {
            if (ev.kind() != MasterEvent.Kind.DEPOSITED) continue;
            MasterArchiveRef ref = master.describe(tenant, ev.masterArchiveId()).orElse(null);
            Outcome o = tx.execute(s -> onDeposited(tenant, ev, ref));
            outcomes.merge(o, 1, Integer::sum);
        }
        LOG.info("[import] {} : rattrapage {} jour(s), {} dépôt(s) lu(s) : {}", tenant.value(), days, events.size(), outcomes);
        return new CatchUp(days, events.size(), outcomes);
    }

    // ─── Décisions de l'archiviste ───────────────────────────────────────────────

    /** Importe une archive de la liste : la cote est revérifiée chez le maître, au nom de l'archiviste. */
    public MasterImportCandidate importCandidate(UUID id, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        MasterImportCandidate c = load(tenant, id);
        if (c.status() != Status.PENDING) {
            throw new IllegalStateException("Cette archive n'est plus à décider (" + label(c.status()) + ").");
        }
        try {
            importFromMaster.execute(Cote.of(c.cote()), actor);  // solde la candidate et l'audite
        } catch (RecordNotFoundException e) {
            throw new RecordNotFoundException("La cote " + c.cote() + " n'est plus trouvée dans la GED maître "
                + "(supprimée, ou invisible pour le compte de JokkoRM) : vous pouvez l'ignorer.");
        }
        return load(tenant, id);
    }

    /** « Pas de papier » : l'archive sort de la liste. Réversible. */
    public MasterImportCandidate ignore(UUID id, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        return tx.execute(s -> {
            MasterImportCandidate c = load(tenant, id);
            if (c.status() != Status.PENDING && c.status() != Status.UNREADABLE) {
                throw new IllegalStateException("Seule une archive à décider peut être ignorée (elle est " + label(c.status()) + ").");
            }
            if (!candidates.decide(tenant, id, c.status(), Status.IGNORED, actor, clock.instant())) {
                throw new IllegalStateException("Cette archive vient d'être décidée par quelqu'un d'autre.");
            }
            audit.record(AuditAction.MASTER_CANDIDATE_IGNORED, AuditTargetType.MASTER_IMPORT_CANDIDATE, id, actor,
                trace(c));
            return load(tenant, id);
        });
    }

    /** Remet dans la liste une archive écartée par erreur. */
    public MasterImportCandidate restore(UUID id, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        return tx.execute(s -> {
            MasterImportCandidate c = load(tenant, id);
            if (c.status() != Status.IGNORED) {
                throw new IllegalStateException("Seule une archive ignorée peut être remise dans la liste.");
            }
            Status back = c.cote() == null ? Status.UNREADABLE : Status.PENDING;
            if (!candidates.decide(tenant, id, Status.IGNORED, back, actor, clock.instant())) {
                throw new IllegalStateException("Cette archive vient d'être décidée par quelqu'un d'autre.");
            }
            audit.record(AuditAction.MASTER_CANDIDATE_RESTORED, AuditTargetType.MASTER_IMPORT_CANDIDATE, id, actor,
                trace(c));
            return load(tenant, id);
        });
    }

    /** Importe d'un coup les archives en attente d'un profil (après avoir activé sa règle, par exemple). */
    public Bulk importPendingOfProfile(String profileReference, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        String profile = requireProfile(profileReference);
        int ok = 0;
        int failed = 0;
        String firstError = null;
        for (MasterImportCandidate c : candidates.pendingOfProfile(tenant, profile, 200)) {
            try {
                importCandidate(c.id(), actor);
                ok++;
            } catch (MasterUnreachableException e) {
                throw e;  // panne : inutile d'insister sur les suivantes
            } catch (RuntimeException e) {
                failed++;
                if (firstError == null) firstError = c.cote() + " : " + e.getMessage();
            }
        }
        return new Bulk(ok, failed, firstError);
    }

    // ─── Règle par profil (responsable des archives) ─────────────────────────────

    /**
     * « Ce profil a toujours du papier » : ses PROCHAINS dépôts entreront seuls. Décision du
     * responsable des archives, auditée avec son nom et la date (activation comme retrait).
     */
    public MasterImportProfile setAutoImport(String profileReference, boolean autoImport, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        String profile = requireProfile(profileReference);
        return tx.execute(s -> {
            UUID id = profiles.find(tenant, profile).map(MasterImportProfile::id).orElseGet(UUID::randomUUID);
            MasterImportProfile saved = profiles.save(
                new MasterImportProfile(id, tenant, profile, autoImport, actor, clock.instant()));
            audit.record(AuditAction.MASTER_AUTO_IMPORT_CHANGED, AuditTargetType.MASTER_IMPORT_PROFILE, saved.id(), actor,
                "profil=" + profile + "; import-auto=" + (autoImport ? "oui" : "non"));
            return saved;
        });
    }

    // ─── Consultation ────────────────────────────────────────────────────────────

    public Summary summary(TenantId tenant) {
        if (!master.publishesEvents(tenant)) return new Summary(false, 0, 0, List.of());
        return tx.execute(s -> {
            Map<String, ProfileView> views = new TreeMap<>();
            for (MasterImportProfile r : profiles.list(tenant)) {
                views.put(r.profileReference(), new ProfileView(r.profileReference(), r.autoImport(), r.decidedBy(),
                    r.decidedAt(), 0));
            }
            for (var pc : candidates.profiles(tenant)) {
                ProfileView v = views.get(pc.profileReference());
                views.put(pc.profileReference(), v == null
                    ? new ProfileView(pc.profileReference(), false, null, null, pc.pending())
                    : new ProfileView(v.profileReference(), v.autoImport(), v.decidedBy(), v.decidedAt(), pc.pending()));
            }
            return new Summary(true, candidates.count(tenant, Status.PENDING), candidates.count(tenant, Status.UNREADABLE),
                new ArrayList<>(views.values()));
        });
    }

    /** La liste, avec le titre lu en direct chez le maître pour les archives à décider. */
    public List<Item> list(Status status, int limit) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        TenantId tenant = TenantContext.current();
        int max = Math.max(1, Math.min(limit, 200));
        List<MasterImportCandidate> rows = tx.execute(s -> candidates.list(tenant, status, max));
        List<Item> items = new ArrayList<>();
        boolean masterDown = false;
        for (MasterImportCandidate c : rows) {
            boolean live = status == Status.PENDING && c.cote() != null && items.size() < LIVE_TITLES && !masterDown;
            if (!live) {
                items.add(new Item(c, null, null, status == Status.PENDING && c.cote() != null));
                continue;
            }
            try {
                Optional<MasterArchiveView> view = master.fetchByCote(tenant, Cote.of(c.cote()));
                items.add(new Item(c, view.map(MasterArchiveView::title).orElse(null),
                    view.map(MasterArchiveView::masterPageUrl).orElse(null), view.isEmpty()));
            } catch (MasterUnreachableException e) {
                masterDown = true;  // une seule tentative : la liste s'affiche quand même
                items.add(new Item(c, null, null, true));
            }
        }
        return items;
    }

    // ─── Outils ──────────────────────────────────────────────────────────────────

    private MasterImportCandidate load(TenantId tenant, UUID id) {
        return candidates.find(tenant, id)
            .orElseThrow(() -> new RecordNotFoundException("Archive à importer introuvable : " + id));
    }

    private void requireJournal(TenantId tenant) {
        if (!master.publishesEvents(tenant)) {
            throw new IllegalStateException("La GED maître de cet espace ne publie pas de journal : rien à rattraper.");
        }
    }

    private static String requireProfile(String raw) {
        String p = raw == null ? "" : raw.trim();
        if (!PROFILE.matcher(p).matches()) {
            throw new IllegalArgumentException("Référence de profil invalide : " + raw);
        }
        return p;
    }

    private static String trace(MasterImportCandidate c) {
        return "cote=" + c.cote() + "; archive-maitre=" + c.masterArchiveId() + "; profil=" + c.profileReference()
            + "; evenement-maitre=" + c.masterEventId();
    }

    static String label(Status s) {
        return switch (s) {
            case PENDING -> "à décider";
            case IMPORTED -> "déjà importée";
            case IGNORED -> "ignorée";
            case UNREADABLE -> "illisible";
        };
    }
}
