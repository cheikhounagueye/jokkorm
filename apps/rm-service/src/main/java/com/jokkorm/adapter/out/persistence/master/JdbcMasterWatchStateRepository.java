package com.jokkorm.adapter.out.persistence.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterWatchState;
import com.jokkorm.domain.master.MasterWatchStateRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMasterWatchStateRepository implements MasterWatchStateRepository {

    private final JdbcTemplate jdbc;

    public JdbcMasterWatchStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MasterWatchState> find(TenantId tenant) {
        return jdbc.query("SELECT tenant_id, cursor_at, last_run_at, last_success_at, last_error, last_events "
            + "FROM master_watch WHERE tenant_id = ?", JdbcMasterWatchStateRepository::map, tenant.value())
            .stream().findFirst();
    }

    @Override
    public void save(MasterWatchState s) {
        Timestamp now = Timestamp.from(s.lastRunAt() == null ? s.cursorAt() : s.lastRunAt());
        jdbc.update("""
            INSERT INTO master_watch (tenant_id, cursor_at, last_run_at, last_success_at, last_error, last_events, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id) DO UPDATE
               SET cursor_at = EXCLUDED.cursor_at, last_run_at = EXCLUDED.last_run_at,
                   last_success_at = EXCLUDED.last_success_at, last_error = EXCLUDED.last_error,
                   last_events = EXCLUDED.last_events, updated_at = EXCLUDED.updated_at
            """, s.tenant().value(), Timestamp.from(s.cursorAt()), ts(s.lastRunAt()), ts(s.lastSuccessAt()),
            truncate(s.lastError()), s.lastEvents(), now);
    }

    @Override
    public List<TenantId> tenants() {
        return jdbc.query("SELECT id FROM tenant ORDER BY id", (rs, i) -> TenantId.of(rs.getString("id")));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "…";
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static MasterWatchState map(ResultSet rs, int i) throws SQLException {
        return new MasterWatchState(TenantId.of(rs.getString("tenant_id")), instant(rs, "cursor_at"),
            instant(rs, "last_run_at"), instant(rs, "last_success_at"), rs.getString("last_error"),
            rs.getInt("last_events"));
    }
}
