package com.jokkorm.adapter.in.scheduling;

import com.jokkorm.application.master.MasterWatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lit le journal du maître toutes les minutes (veilleur, étape 1). Ne concerne que les tenants
 * dont le maître publie un journal (Maarch RM). Désactivable par
 * {@code jokkorm.master.watch.enabled=false} (les tests appellent {@link MasterWatchService#watch}).
 */
@Component
@ConditionalOnProperty(name = "jokkorm.master.watch.enabled", havingValue = "true", matchIfMissing = true)
public class MasterWatchWorker {

    private final MasterWatchService watcher;

    public MasterWatchWorker(MasterWatchService watcher) {
        this.watcher = watcher;
    }

    @Scheduled(fixedDelayString = "${jokkorm.master.watch.delay-ms:60000}",
        initialDelayString = "${jokkorm.master.watch.initial-delay-ms:20000}")
    public void run() {
        watcher.watchAll();
    }
}
