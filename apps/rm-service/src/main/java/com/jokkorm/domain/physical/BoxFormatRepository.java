package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoxFormatRepository {
    BoxFormat save(BoxFormat format);
    Optional<BoxFormat> findById(TenantId tenant, UUID id);
    Optional<BoxFormat> findByCode(TenantId tenant, String code);
    List<BoxFormat> findAll(TenantId tenant);
}
