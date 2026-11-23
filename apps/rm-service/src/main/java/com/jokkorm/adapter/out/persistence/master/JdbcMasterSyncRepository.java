package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterSync;
import com.jokkorm.domain.master.MasterSyncRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * File d'envoi vers le maître, en SQL : l'upsert {@code ON CONFLICT} garde une seule ligne par
 * cote même si deux gestes arrivent en même temps. JdbcTemplate participe à la transaction JPA
 * en cours : la demande d'envoi est validée ou annulée avec le geste physique.
 */
@Repository
public class JdbcMasterSyncRepository implements MasterSyncRepository {

    private static final String COLUMNS =
        "tenant_id, cote, status, attempts, next_attempt_at, last_attempt_at, last_sent_at, last_error, last_payload, version";

    private final JdbcTemplate jdbc;

    public JdbcMasterSyncRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void touch(TenantId tenant, String cote, Instant at) {
        Timestamp ts = Timestamp.from(at);
        jdbc.update("""
            INSERT INTO master_sync (tenant_id, cote, status, attempts, next_attempt_at, created_at, updated_at, version)
            VALUES (?, ?, 'PENDING', 0, ?, ?, ?, 0)
            ON CONFLICT (tenant_id, cote) DO UPDATE
               SET status = 'PENDING', attempts = 0, next_attempt_at = EXCLUDED.next_attempt_at,
                   updated_at = EXCLUDED.updated_at, version = master_sync.version + 1
            """, tenant.value(), cote, ts, ts, ts);
    }

    @Override
    public List<MasterSync> findDue(Instant at, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_sync WHERE status = 'PENDING' AND next_attempt_at <= ? "
            + "ORDER BY next_attempt_at LIMIT ?", JdbcMasterSyncRepository::map, Timestamp.from(at), limit);
    }

    @Override
    public Optional<MasterSync> find(TenantId tenant, String cote) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_sync WHERE tenant_id = ? AND cote = ?",
            JdbcMasterSyncRepository::map, tenant.value(), cote).stream().findFirst();
    }

    @Override
    public boolean markSent(TenantId tenant, String cote, long version, String payload, Instant at) {
        Timestamp ts = Timestamp.from(at);
        return jdbc.update("""
            UPDATE master_sync SET status = 'SENT', attempts = attempts + 1, last_attempt_at = ?, last_sent_at = ?,
                   last_error = NULL, last_payload = ?, updated_at = ?
             WHERE tenant_id = ? AND cote = ? AND version = ?
            """, ts, ts, truncate(payload), ts, tenant.value(), cote, version) == 1;
    }

    @Override
    public void markRetry(TenantId tenant, String cote, long version, String error, Instant at, Instant nextAttemptAt) {
        jdbc.update("""
            UPDATE master_sync SET attempts = attempts + 1, last_attempt_at = ?, last_error = ?, next_attempt_at = ?,
                   updated_at = ?
             WHERE tenant_id = ? AND cote = ? AND version = ?
            """, Timestamp.from(at), truncate(error), Timestamp.from(nextAttemptAt), Timestamp.from(at),
            tenant.value(), cote, version);
    }

    @Override
    public void markRejected(TenantId tenant, String cote, long version, String error, Instant at) {
        jdbc.update("""
            UPDATE master_sync SET status = 'REJECTED', attempts = attempts + 1, last_attempt_at = ?, last_error = ?,
                   updated_at = ?
             WHERE tenant_id = ? AND cote = ? AND version = ?
            """, Timestamp.from(at), truncate(error), Timestamp.from(at), tenant.value(), cote, version);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "…";
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static MasterSync map(ResultSet rs, int i) throws SQLException {
        return new MasterSync(TenantId.of(rs.getString("tenant_id")), rs.getString("cote"),
            MasterSync.Status.valueOf(rs.getString("status")), rs.getInt("attempts"), instant(rs, "next_attempt_at"),
            instant(rs, "last_attempt_at"), instant(rs, "last_sent_at"), rs.getString("last_error"),
            rs.getString("last_payload"), rs.getLong("version"));
    }
}
