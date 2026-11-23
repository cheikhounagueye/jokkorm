package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterAlert;
import com.jokkorm.domain.master.MasterAlertRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMasterAlertRepository implements MasterAlertRepository {

    private static final String COLUMNS = "id, tenant_id, kind, cote, record_id, master_event_id, occurred_at, actor, "
        + "detail, created_at, acknowledged_at, acknowledged_by";
    /** Types qui demandent une action, pour le filtre « à traiter » (liste fixe, pas d'entrée utilisateur). */
    private static final String ACTION_KINDS = Arrays.stream(MasterAlert.Kind.values())
        .filter(MasterAlert.Kind::requiresAction)
        .map(k -> "'" + k.name() + "'")
        .collect(Collectors.joining(", "));

    private final JdbcTemplate jdbc;

    public JdbcMasterAlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(MasterAlert a) {
        return jdbc.update("INSERT INTO master_alert (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, master_event_id, kind) DO NOTHING",
            a.id(), a.tenant().value(), a.kind().name(), a.cote(), a.recordId(), a.masterEventId(),
            Timestamp.from(a.occurredAt()), truncate(a.actor(), 255), truncate(a.detail(), 500),
            Timestamp.from(a.createdAt()), ts(a.acknowledgedAt()), a.acknowledgedBy()) == 1;
    }

    @Override
    public List<MasterAlert> list(TenantId tenant, boolean onlyOpenActions, int limit) {
        String where = onlyOpenActions ? " AND acknowledged_at IS NULL AND kind IN (" + ACTION_KINDS + ")" : "";
        return jdbc.query("SELECT " + COLUMNS + " FROM master_alert WHERE tenant_id = ?" + where
            + " ORDER BY created_at DESC, occurred_at DESC LIMIT ?", JdbcMasterAlertRepository::map, tenant.value(), limit);
    }

    @Override
    public int countOpenActions(TenantId tenant) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM master_alert WHERE tenant_id = ? "
            + "AND acknowledged_at IS NULL AND kind IN (" + ACTION_KINDS + ")", Integer.class, tenant.value());
        return n == null ? 0 : n;
    }

    @Override
    public boolean acknowledge(TenantId tenant, UUID id, String by, Instant at) {
        return jdbc.update("UPDATE master_alert SET acknowledged_at = ?, acknowledged_by = ? "
            + "WHERE tenant_id = ? AND id = ? AND acknowledged_at IS NULL",
            Timestamp.from(at), truncate(by, 255), tenant.value(), id) == 1;
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

    private static MasterAlert map(ResultSet rs, int i) throws SQLException {
        return new MasterAlert(rs.getObject("id", UUID.class), TenantId.of(rs.getString("tenant_id")),
            MasterAlert.Kind.valueOf(rs.getString("kind")), rs.getString("cote"), rs.getObject("record_id", UUID.class),
            rs.getString("master_event_id"), instant(rs, "occurred_at"), rs.getString("actor"), rs.getString("detail"),
            instant(rs, "created_at"), instant(rs, "acknowledged_at"), rs.getString("acknowledged_by"));
    }
}
