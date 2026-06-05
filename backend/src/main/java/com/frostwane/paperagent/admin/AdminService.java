package com.frostwane.paperagent.admin;

import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelUsageResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ParseJobResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RecentPaperResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.StatusCountResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRepository;
import com.frostwane.paperagent.user.UserRole;
import com.frostwane.paperagent.user.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public AdminService(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview(User currentUser) {
        requireAdmin(currentUser);
        return new AdminOverviewResponse(
            count("select count(*) from users"),
            count("select count(*) from users where status = 'NORMAL'"),
            count("select count(*) from users where status = 'DISABLED'"),
            count("select count(*) from papers"),
            count("select count(*) from papers where process_status = 'INDEXED'"),
            count("select count(*) from papers where process_status = 'FAILED'"),
            count("select count(*) from paper_files"),
            count("select coalesce(sum(size), 0) from paper_files"),
            count("select count(*) from paper_chunks"),
            count("select count(*) from paper_chunks where embedding is not null"),
            count("select count(*) from chat_records"),
            count("select count(*) from chat_records where paper_id is null"),
            intValue("select coalesce(round(avg(latency_ms)), 0) from chat_records where latency_ms is not null"),
            count("select count(*) from rag_traces where status = 'FAILED'"),
            intValue("select coalesce(round(avg(retrieval_ms)), 0) from rag_traces"),
            intValue("select coalesce(round(avg(generation_ms)), 0) from rag_traces"),
            count("select count(*) from parse_jobs"),
            count("select count(*) from parse_jobs where status = 'FAILED'"),
            intValue("select coalesce(round(avg(duration_ms)), 0) from parse_jobs where finished_at is not null"),
            processStatuses(),
            modelUsage(),
            recentPapers(),
            recentParseJobs(),
            recentTraces()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users(User currentUser) {
        requireAdmin(currentUser);
        return jdbcTemplate.query("""
            select
              u.id,
              u.username,
              u.email,
              u.role,
              u.status,
              u.created_at,
              (select count(*) from papers p where p.owner_id = u.id) as paper_count,
              (select count(*) from papers p where p.owner_id = u.id and p.process_status = 'INDEXED') as indexed_paper_count,
              (select count(*) from chat_records c where c.owner_id = u.id) as chat_count,
              (select count(*) from paper_files f where f.owner_id = u.id) as file_count,
              (select coalesce(sum(f.size), 0) from paper_files f where f.owner_id = u.id) as storage_bytes,
              (select coalesce(round(avg(c.latency_ms)), 0) from chat_records c where c.owner_id = u.id and c.latency_ms is not null) as average_latency_ms
            from users u
            order by u.created_at desc
            limit 80
            """, (rs, rowNum) -> new AdminUserResponse(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getLong("paper_count"),
            rs.getLong("indexed_paper_count"),
            rs.getLong("chat_count"),
            rs.getLong("file_count"),
            rs.getLong("storage_bytes"),
            rs.getInt("average_latency_ms"),
            offsetDateTime(rs, "created_at")
        ));
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, UserStatus status, User currentUser) {
        requireAdmin(currentUser);
        if (currentUser.getId().equals(userId) && status == UserStatus.DISABLED) {
            throw new BusinessException("不能禁用当前登录的管理员账号");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        user.setStatus(status);
        userRepository.saveAndFlush(user);
        return users(currentUser).stream()
            .filter(item -> item.id().equals(userId))
            .findFirst()
            .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private List<StatusCountResponse> processStatuses() {
        return jdbcTemplate.query("""
            select process_status, count(*) as total
            from papers
            group by process_status
            order by total desc, process_status asc
            """, (rs, rowNum) -> new StatusCountResponse(rs.getString("process_status"), rs.getLong("total")));
    }

    private List<ModelUsageResponse> modelUsage() {
        return jdbcTemplate.query("""
            select coalesce(model_name, 'fallback') as model_name,
                   count(*) as total,
                   coalesce(round(avg(latency_ms)), 0) as average_latency_ms
            from chat_records
            group by coalesce(model_name, 'fallback')
            order by total desc, model_name asc
            limit 6
            """, (rs, rowNum) -> new ModelUsageResponse(
            rs.getString("model_name"),
            rs.getLong("total"),
            rs.getInt("average_latency_ms")
        ));
    }

    private List<RecentPaperResponse> recentPapers() {
        return jdbcTemplate.query("""
            select p.id, p.title, u.username, p.process_status, p.updated_at
            from papers p
            join users u on u.id = p.owner_id
            order by p.updated_at desc
            limit 8
            """, (rs, rowNum) -> new RecentPaperResponse(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("username"),
            rs.getString("process_status"),
            offsetDateTime(rs, "updated_at")
        ));
    }

    private List<RagTraceResponse> recentTraces() {
        return jdbcTemplate.query("""
            select
              t.id,
              u.username,
              p.id as paper_id,
              p.title as paper_title,
              t.scope,
              t.question,
              t.status,
              coalesce(t.model_name, 'fallback') as model_name,
              t.source_count,
              t.retrieval_ms,
              t.generation_ms,
              t.verification_ms,
              t.formatting_ms,
              t.total_ms,
              t.error_message,
              t.created_at
            from rag_traces t
            join users u on u.id = t.owner_id
            left join papers p on p.id = t.paper_id
            order by t.created_at desc
            limit 8
            """, (rs, rowNum) -> new RagTraceResponse(
            rs.getLong("id"),
            rs.getString("username"),
            nullableLong(rs, "paper_id"),
            rs.getString("paper_title"),
            rs.getString("scope"),
            rs.getString("question"),
            rs.getString("status"),
            rs.getString("model_name"),
            rs.getInt("source_count"),
            rs.getInt("retrieval_ms"),
            rs.getInt("generation_ms"),
            rs.getInt("verification_ms"),
            rs.getInt("formatting_ms"),
            rs.getInt("total_ms"),
            rs.getString("error_message"),
            offsetDateTime(rs, "created_at")
        ));
    }

    private List<ParseJobResponse> recentParseJobs() {
        return jdbcTemplate.query("""
            select
              j.id,
              u.username,
              j.paper_id,
              j.paper_title,
              j.file_name,
              j.file_size,
              j.status,
              j.page_count,
              j.chunk_count,
              j.duration_ms,
              j.error_message,
              j.started_at,
              j.finished_at
            from parse_jobs j
            join users u on u.id = j.owner_id
            order by j.started_at desc
            limit 8
            """, (rs, rowNum) -> new ParseJobResponse(
            rs.getLong("id"),
            rs.getString("username"),
            nullableLong(rs, "paper_id"),
            rs.getString("paper_title"),
            rs.getString("file_name"),
            rs.getLong("file_size"),
            rs.getString("status"),
            rs.getInt("page_count"),
            rs.getInt("chunk_count"),
            rs.getInt("duration_ms"),
            rs.getString("error_message"),
            offsetDateTime(rs, "started_at"),
            offsetDateTime(rs, "finished_at")
        ));
    }

    private long count(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private int intValue(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        return null;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
