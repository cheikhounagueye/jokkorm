package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterImportProfile;
import com.jokkorm.domain.master.MasterImportProfileRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMasterImportProfileRepository implements MasterImportProfileRepository {

    private static final String COLUMNS = "id, tenant_id, profile_reference, auto_import, decided_by, decided_at";

    private final JdbcTemplate jdbc;

    public JdbcMasterImportProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MasterImportProfile> find(TenantId tenant, String profileReference) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_import_profile WHERE tenant_id = ? AND profile_reference = ?",
            JdbcMasterImportProfileRepository::map, tenant.value(), profileReference).stream().findFirst();
    }

    @Override
    public List<MasterImportProfile> list(TenantId tenant) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_import_profile WHERE tenant_id = ? ORDER BY profile_reference",
            JdbcMasterImportProfileRepository::map, tenant.value());
    }

    @Override
    public MasterImportProfile save(MasterImportProfile r) {
        return jdbc.query("INSERT INTO master_import_profile (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, profile_reference) DO UPDATE SET auto_import = EXCLUDED.auto_import, "
                + "decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at RETURNING " + COLUMNS,
            JdbcMasterImportProfileRepository::map, r.id(), r.tenant().value(), r.profileReference(), r.autoImport(),
            r.decidedBy(), Timestamp.from(r.decidedAt())).get(0);
    }

    private static MasterImportProfile map(ResultSet rs, int i) throws SQLException {
        return new MasterImportProfile(rs.getObject("id", UUID.class), TenantId.of(rs.getString("tenant_id")),
            rs.getString("profile_reference"), rs.getBoolean("auto_import"), rs.getString("decided_by"),
            rs.getTimestamp("decided_at").toInstant());
    }
}
