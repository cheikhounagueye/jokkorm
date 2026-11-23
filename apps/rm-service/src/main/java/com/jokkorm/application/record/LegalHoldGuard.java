package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Gel contentieux et sortie physique (pattern 01, « deux gardiens ») : une boîte ne quitte
 * pas la salle, et aucune pièce n'en sort, si l'une des archives qu'elle contient est gelée,
 * que le gel ait été posé dans JokkoRM ({@code FROZEN}) ou dans la GED maître
 * ({@code legalHold}).
 *
 * <p>Fermé en cas de doute : si le maître ne répond pas, le gel n'est pas vérifiable et la
 * sortie est refusée. Une cote inconnue du maître n'a pas de gel maître : seul le gel local
 * compte alors.
 */
@Service
public class LegalHoldGuard {

    /**
     * @param records  nombre d'archives de la boîte vérifiées
     * @param blockers raisons de refuser la sortie (vide = sortie possible)
     */
    public record Verdict(int records, List<String> blockers) {
        public boolean blocked() { return !blockers.isEmpty(); }
    }

    private final PhysicalArchiveRecordRepository records;
    private final SaeMasterPort master;

    public LegalHoldGuard(PhysicalArchiveRecordRepository records, SaeMasterPort master) {
        this.records = records;
        this.master = master;
    }

    public Verdict check(TenantId tenant, UUID containerId) {
        List<PhysicalArchiveRecord> inBox = records.findByContainer(tenant, containerId);
        List<String> blockers = new ArrayList<>();
        for (PhysicalArchiveRecord r : inBox) {
            String cote = r.cote().value();
            if (r.status() == PhysicalArchiveStatus.FROZEN) {
                blockers.add("Gel contentieux posé dans JokkoRM sur la cote " + cote);
            }
            try {
                master.fetchByCote(tenant, r.cote())
                    .filter(view -> view.legalHold())
                    .ifPresent(view -> blockers.add("Gel contentieux posé dans la GED maître sur la cote " + cote));
            } catch (MasterUnreachableException e) {
                blockers.add("Gel non vérifiable pour la cote " + cote + " : la GED maître ne répond pas ("
                    + e.getMessage() + ")");
            }
        }
        return new Verdict(inBox.size(), List.copyOf(blockers));
    }

    /** Lève {@link LegalHoldException} si la boîte ne peut pas sortir. */
    public void requireNoHold(TenantId tenant, UUID containerId, String barcode) {
        Verdict verdict = check(tenant, containerId);
        if (verdict.blocked()) {
            throw new LegalHoldException("Sortie " + (barcode == null ? "de la boîte" : "de la boîte " + barcode) + " refusée : "
                + String.join(" ; ", verdict.blockers()));
        }
    }
}
