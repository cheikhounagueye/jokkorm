package com.jokkorm.adapter.in.scheduling;

import com.jokkorm.application.master.MasterSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Envoie régulièrement au maître l'état physique des cotes en attente (tranche 2a).
 * Désactivable par {@code jokkorm.master.sync.worker-enabled=false} (les tests appellent
 * {@link MasterSyncService#drain} eux-mêmes).
 */
@Component
@ConditionalOnProperty(name = "jokkorm.master.sync.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MasterSyncWorker {

    private final MasterSyncService sync;

    public MasterSyncWorker(MasterSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "${jokkorm.master.sync.delay-ms:5000}", initialDelayString = "${jokkorm.master.sync.initial-delay-ms:10000}")
    public void run() {
        sync.drain(50);
    }
}
