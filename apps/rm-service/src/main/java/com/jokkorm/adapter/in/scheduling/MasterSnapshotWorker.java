package com.jokkorm.adapter.in.scheduling;

import com.jokkorm.application.master.MasterSnapshotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relit chez le maître les copies de rangement manquantes ou trop anciennes (D3) : ce que le
 * veilleur ne voit pas (règle de conservation modifiée…) finit ainsi par être recopié. Ne fait rien
 * pour un espace sans maître. Désactivable par {@code jokkorm.master.snapshot.worker-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "jokkorm.master.snapshot.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MasterSnapshotWorker {

    private final MasterSnapshotService snapshots;
    private final int batch;

    public MasterSnapshotWorker(MasterSnapshotService snapshots,
                                @Value("${jokkorm.master.snapshot.batch:50}") int batch) {
        this.snapshots = snapshots;
        this.batch = Math.max(1, batch);
    }

    @Scheduled(fixedDelayString = "${jokkorm.master.snapshot.delay-ms:300000}",
        initialDelayString = "${jokkorm.master.snapshot.initial-delay-ms:30000}")
    public void run() {
        snapshots.refreshAllStale(batch);
    }
}
