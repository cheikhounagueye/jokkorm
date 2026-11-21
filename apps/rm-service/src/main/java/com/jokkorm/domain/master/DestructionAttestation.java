package com.jokkorm.domain.master;

import java.time.Instant;
import java.util.Objects;

/**
 * Attestation d'exécution de destruction. Le PDF signé est généré par JokkoRM
 * (PDFBox + HMAC chaîné) et remonté au maître qui l'attache au bordereau d'élimination.
 *
 * @param attestationId identifiant stable (UUID)
 * @param executedAt    horodatage de l'exécution physique
 * @param agentSubject  agent qui a exécuté (claim OIDC {@code sub})
 * @param signedPdf     PDF scellé, prêt à être archivé côté maître
 */
public record DestructionAttestation(
    String attestationId, Instant executedAt, String agentSubject, byte[] signedPdf) {

    public DestructionAttestation {
        Objects.requireNonNull(attestationId, "attestationId");
        Objects.requireNonNull(executedAt, "executedAt");
        Objects.requireNonNull(agentSubject, "agentSubject");
        Objects.requireNonNull(signedPdf, "signedPdf");
        if (signedPdf.length == 0) {
            throw new IllegalArgumentException("signedPdf is empty");
        }
    }
}
