package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.BoxFormatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue de formats de boîtes du tenant. Dos, profondeur et hauteur sont immuables :
 * ils mesurent des boîtes déjà rangées. Pour un autre gabarit, on crée un autre format.
 */
@Service
public class BoxFormatAdminUseCase {

    private final BoxFormatRepository formats;
    private final BoxFormatCatalog catalog;
    private final AuditChainService audit;
    private final Clock clock;

    public BoxFormatAdminUseCase(BoxFormatRepository formats, BoxFormatCatalog catalog,
                                 AuditChainService audit, Clock clock) {
        this.formats = formats;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
    }

    /** Liste le catalogue (installe le catalogue standard au premier appel d'un nouveau tenant). */
    @Transactional
    public List<BoxFormat> list() {
        TenantId tenant = TenantContext.current();
        catalog.defaultFormat(tenant);
        return formats.findAll(tenant);
    }

    public record NewFormat(String code, String label, int spineMm, int depthMm, int heightMm,
                            BoxFormat.Quality quality, Integer maxWeightKg) {}

    @Transactional
    public BoxFormat create(NewFormat f, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        catalog.defaultFormat(tenant);
        String code = f.code() == null ? null : f.code().trim().toUpperCase(java.util.Locale.ROOT);
        if (code != null && formats.findByCode(tenant, code).isPresent()) {
            throw new StorageLocationConflictException("Le format " + code + " existe déjà");
        }
        BoxFormat created = formats.save(BoxFormat.create(tenant, code, f.label(), f.spineMm(), f.depthMm(),
            f.heightMm(), f.quality() == null ? BoxFormat.Quality.STANDARD : f.quality(), f.maxWeightKg(),
            clock.instant()));
        audit.record(AuditAction.BOX_FORMAT_CREATED, AuditTargetType.BOX_FORMAT, created.id(), actor,
            "code=%s spine=%d depth=%d height=%d".formatted(created.code(), created.spineMm(),
                created.depthMm(), created.heightMm()));
        return created;
    }

    public record FormatChange(String label, BoxFormat.Quality quality, Integer maxWeightKg,
                               Boolean active, Boolean makeDefault) {}

    @Transactional
    public BoxFormat update(UUID id, FormatChange change, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        Instant now = clock.instant();
        BoxFormat f = formats.findById(tenant, id)
            .orElseThrow(() -> new IllegalArgumentException("Format de boîte inconnu : " + id));

        f.describe(change.label() == null ? f.label() : change.label(),
            change.quality() == null ? f.quality() : change.quality(),
            change.maxWeightKg() == null ? f.maxWeightKg().orElse(null) : change.maxWeightKg(), now);
        if (Boolean.TRUE.equals(change.active())) f.setActive(true, now);
        if (Boolean.TRUE.equals(change.makeDefault()) && !f.isDefault()) {
            if (!f.active()) throw new StorageLocationConflictException("Un format retiré ne peut pas devenir le défaut");
            formats.findAll(tenant).stream().filter(BoxFormat::isDefault).forEach(previous -> {
                previous.setDefault(false, now);
                formats.save(previous);
            });
            f.setDefault(true, now);
        }
        if (Boolean.FALSE.equals(change.active())) {
            if (f.isDefault()) {
                throw new StorageLocationConflictException(
                    "Le format par défaut ne peut pas être retiré : choisis d'abord un autre défaut");
            }
            f.setActive(false, now);
        }
        BoxFormat saved = formats.save(f);
        audit.record(AuditAction.BOX_FORMAT_UPDATED, AuditTargetType.BOX_FORMAT, saved.id(), actor,
            "code=%s active=%s default=%s".formatted(saved.code(), saved.active(), saved.isDefault()));
        return saved;
    }
}
