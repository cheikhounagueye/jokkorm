package com.jokkorm.adapter.out.maarch;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.MaarchDigitizePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter staging — stocke les numérisations en local en attendant l'interop
 * Maarch RM réelle (phase 5P.K). Le dossier contient le PDF + un manifest.json
 * avec tout le contexte, pour qu'on puisse rejouer l'upload plus tard.
 *
 * <p>Actif par défaut. Désactivable en passant {@code jokkorm.maarch.digitize.kind=medona}
 * quand le vrai adapter Medona sera prêt.
 */
@Component
@ConditionalOnProperty(name = "jokkorm.maarch.digitize.kind", havingValue = "staging", matchIfMissing = true)
public class StagingMaarchDigitizeAdapter implements MaarchDigitizePort {

    private static final Logger LOG = LoggerFactory.getLogger(StagingMaarchDigitizeAdapter.class);

    private final Path baseDir;

    public StagingMaarchDigitizeAdapter(
        @Value("${jokkorm.maarch.digitize.staging-dir:/tmp/jokkorm/pending-maarch-upload}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    @Override
    public UploadResult upload(TenantId tenant, UUID accessRequestId,
                                String containerBarcode, String cote,
                                String piecesDescription, byte[] pdfBytes) {
        String uuid = UUID.randomUUID().toString();
        Path dir = baseDir.resolve(tenant.value()).resolve(uuid);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("file.pdf"), pdfBytes);
            String manifest = """
                {
                  "staging_id": "%s",
                  "tenant": "%s",
                  "access_request_id": "%s",
                  "container_barcode": "%s",
                  "cote": "%s",
                  "pieces_description": %s,
                  "uploaded_at": "%s",
                  "size_bytes": %d
                }
                """.formatted(
                    uuid, tenant.value(), accessRequestId,
                    containerBarcode == null ? "" : containerBarcode,
                    cote == null ? "" : cote,
                    piecesDescription == null ? "null" : "\"" + piecesDescription.replace("\"", "\\\"") + "\"",
                    Instant.now(), pdfBytes.length);
            Files.writeString(dir.resolve("manifest.json"), manifest, StandardCharsets.UTF_8);
            String resId = "staging:" + uuid;
            String url = dir.toUri().toString();
            LOG.info("Staging upload : access_request={} → {} ({} bytes)",
                accessRequestId, resId, pdfBytes.length);
            return new UploadResult(resId, url, true);
        } catch (IOException e) {
            throw new IllegalStateException("Staging upload failed: " + e.getMessage(), e);
        }
    }
}
