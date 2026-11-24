package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterImportCandidate;
import com.jokkorm.domain.master.MasterImportCandidate.Status;
import com.jokkorm.domain.master.MasterImportCandidateRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMasterImportCandidateRepository implements MasterImportCandidateRepository {

    private static final String COLUMNS = "id, tenant_id, master_archive_id, cote, profile_reference, master_event_id, "
        + "deposited_at, deposited_by, status, auto, decided_by, decided_at, created_at";

    private final JdbcTemplate jdbc;

    public JdbcMasterImportCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(MasterImportCandidate c) {
        return jdbc.update("INSERT INTO master_import_candidate (" + COLUMNS + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, master_archive_id) DO NOTHING",
            c.id(), c.tenant().value(), c.masterArchiveId(), c.cote(), c.profileReference(), c.masterEventId(),
            Timestamp.from(c.depositedAt()), truncate(c.depositedBy(), 255), c.status().name(), c.auto(),
            truncate(c.decidedBy(), 255), ts(c.decidedAt()), Timestamp.from(c.createdAt())) == 1;
    }

    @Override
    public void deleteUnreadable(TenantId tenant, String masterArchiveId) {
        jdbc.update("DELETE FROM master_import_candidate WHERE tenant_id = ? AND master_archive_id = ? AND status = ?",
            tenant.value(), masterArchiveId, Status.UNREADABLE.name());
    }

    @Override
    public Optional<MasterImportCandidate> find(TenantId tenant, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_import_candidate WHERE tenant_id = ? AND id = ?",
            JdbcMasterImportCandidateRepository::map, tenant.value(), id).stream().findFirst();
    }

    @Override
    public List<MasterImportCandidate> list(TenantId tenant, Status status, int limit) {
        // À décider : les plus anciens d'abord (file d'attente) ; historique : les plus récents d'abord.
        String order = status == Status.PENDING ? "deposited_at ASC" : "COALESCE(decided_at, created_at) DESC";
        return jdbc.query("SELECT " + COLUMNS + " FROM master_import_candidate WHERE tenant_id = ? AND status = ? "
            + "ORDER BY " + order + " LIMIT ?", JdbcMasterImportCandidateRepository::map, tenant.value(), status.name(), limit);
    }

    @Override
    public int count(TenantId tenant, Status status) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM master_import_candidate WHERE tenant_id = ? AND status = ?",
            Integer.class, tenant.value(), status.name());
        return n == null ? 0 : n;
    }

    @Override
    public List<MasterImportCandidate> pendingOfProfile(TenantId tenant, String profileReference, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM master_import_candidate WHERE tenant_id = ? AND status = ? "
                + "AND profile_reference = ? ORDER BY deposited_at ASC LIMIT ?", JdbcMasterImportCandidateRepository::map,
            tenant.value(), Status.PENDING.name(), profileReference, limit);
    }

    @Override
    public List<ProfileCount> profiles(TenantId tenant) {
        return jdbc.query("SELECT profile_reference, count(*) FILTER (WHERE status = ?) AS pending "
                + "FROM master_import_candidate WHERE tenant_id = ? AND profile_reference IS NOT NULL "
                + "GROUP BY profile_reference ORDER BY profile_reference",
            (rs, i) -> new ProfileCount(rs.getString("profile_reference"), rs.getInt("pending")),
            Status.PENDING.name(), tenant.value());
    }

    @Override
    public boolean decide(TenantId tenant, UUID id, Status from, Status to, String by, Instant at) {
        return jdbc.update("UPDATE master_import_candidate SET status = ?, decided_by = ?, decided_at = ? "
                + "WHERE tenant_id = ? AND id = ? AND status = ?",
            to.name(), truncate(by, 255), Timestamp.from(at), tenant.value(), id, from.name()) == 1;
    }

    @Override
    public Optional<MasterImportCandidate> markImportedByCote(TenantId tenant, String cote, String by, Instant at) {
        return jdbc.query("UPDATE master_import_candidate SET status = ?, decided_by = ?, decided_at = ? "
                + "WHERE tenant_id = ? AND cote = ? AND status = ? RETURNING " + COLUMNS,
            JdbcMasterImportCandidateRepository::map, Status.IMPORTED.name(), truncate(by, 255), Timestamp.from(at),
            tenant.value(), cote, Status.PENDING.name()).stream().findFirst();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static MasterImportCandidate map(ResultSet rs, int i) throws SQLException {
        return new MasterImportCandidate(rs.getObject("id", UUID.class), TenantId.of(rs.getString("tenant_id")),
            rs.getString("master_archive_id"), rs.getString("cote"), rs.getString("profile_reference"),
            rs.getString("master_event_id"), instant(rs, "deposited_at"), rs.getString("deposited_by"),
            Status.valueOf(rs.getString("status")), rs.getBoolean("auto"), rs.getString("decided_by"),
            instant(rs, "decided_at"), instant(rs, "created_at"));
    }
}
