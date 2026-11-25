package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterRecordSnapshot;
import com.jokkorm.domain.master.MasterRecordSnapshotRepository;
import com.jokkorm.domain.record.Cote;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMasterRecordSnapshotRepository implements MasterRecordSnapshotRepository {

    private static final String COLUMNS = "record_id, tenant_id, master_kind, service_code, final_fate, "
        + "final_fate_source, disposal_date, classification_level, legal_hold, fetched_at";

    private final JdbcTemplate jdbc;

    public JdbcMasterRecordSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MasterRecordSnapshot> find(TenantId tenant, UUID recordId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_record_snapshot WHERE tenant_id = ? AND record_id = ?",
            JdbcMasterRecordSnapshotRepository::map, tenant.value(), recordId).stream().findFirst();
    }

    @Override
    public Map<UUID, MasterRecordSnapshot> findAll(TenantId tenant, Collection<UUID> recordIds) {
        Map<UUID, MasterRecordSnapshot> out = new HashMap<>();
        if (recordIds.isEmpty()) return out;
        UUID[] ids = recordIds.toArray(UUID[]::new);
        jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT " + COLUMNS
                + " FROM master_record_snapshot WHERE tenant_id = ? AND record_id = ANY (?)");
            ps.setString(1, tenant.value());
            ps.setArray(2, con.createArrayOf("uuid", ids));
            return ps;
        }, rs -> {
            MasterRecordSnapshot s = map(rs, 0);
            out.put(s.recordId(), s);
        });
        return out;
    }

    @Override
    public void save(MasterRecordSnapshot s) {
        jdbc.update("INSERT INTO master_record_snapshot (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (record_id) DO UPDATE SET master_kind = EXCLUDED.master_kind, "
                + "service_code = EXCLUDED.service_code, final_fate = EXCLUDED.final_fate, "
                + "final_fate_source = EXCLUDED.final_fate_source, disposal_date = EXCLUDED.disposal_date, "
                + "classification_level = EXCLUDED.classification_level, legal_hold = EXCLUDED.legal_hold, "
                + "fetched_at = EXCLUDED.fetched_at "
                // Une copie ne passe jamais d'un tenant à un autre.
                + "WHERE master_record_snapshot.tenant_id = EXCLUDED.tenant_id",
            s.recordId(), s.tenant().value(), s.masterKind(), s.serviceCode(), s.finalFate(),
            s.finalFateSource(), s.disposalDate() == null ? null : Date.valueOf(s.disposalDate()),
            s.classificationLevel(), s.legalHold(), Timestamp.from(s.fetchedAt()));
    }

    @Override
    public List<Stale> findStale(TenantId tenant, java.time.Instant before, int limit) {
        return jdbc.query("SELECT r.id, r.cote FROM physical_archive_record r "
                + "LEFT JOIN master_record_snapshot s ON s.record_id = r.id "
                + "WHERE r.tenant_id = ? AND r.status NOT IN ('DESTROYED', 'TRANSFERRED') "
                + "AND (s.record_id IS NULL OR s.fetched_at < ?) "
                + "ORDER BY s.fetched_at NULLS FIRST, r.registered_at LIMIT ?",
            (rs, i) -> new Stale(rs.getObject("id", UUID.class), Cote.of(rs.getString("cote"))),
            tenant.value(), Timestamp.from(before), limit);
    }

    @Override
    public List<TenantId> tenants() {
        return jdbc.query("SELECT id FROM tenant ORDER BY id", (rs, i) -> TenantId.of(rs.getString("id")));
    }

    private static MasterRecordSnapshot map(ResultSet rs, int i) throws SQLException {
        Date disposal = rs.getDate("disposal_date");
        return new MasterRecordSnapshot(TenantId.of(rs.getString("tenant_id")), rs.getObject("record_id", UUID.class),
            rs.getString("master_kind"), rs.getString("service_code"), rs.getString("final_fate"),
            rs.getString("final_fate_source"), disposal == null ? null : disposal.toLocalDate(),
            rs.getString("classification_level"), rs.getBoolean("legal_hold"),
            rs.getTimestamp("fetched_at").toInstant());
    }
}
