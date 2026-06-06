package com.frostwane.paperagent.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelHealthResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelUsageResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ParseJobNodeSpanResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ParseJobResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryExpansionResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceNodeSpanResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceRetrievalChannelResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceRetrievalProcessorResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RecentPaperResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.StatusCountResponse;
import com.frostwane.paperagent.agent.term.QueryTermMapping;
import com.frostwane.paperagent.agent.term.QueryTermMappingRepository;
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
    private final QueryTermMappingRepository queryTermMappingRepository;
    private final ObjectMapper objectMapper;

    public AdminService(
        JdbcTemplate jdbcTemplate,
        UserRepository userRepository,
        QueryTermMappingRepository queryTermMappingRepository,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.queryTermMappingRepository = queryTermMappingRepository;
        this.objectMapper = objectMapper;
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
            count("select count(*) from chat_records where feedback_score is not null"),
            count("select count(*) from chat_records where feedback_score = 1"),
            count("select count(*) from chat_records where feedback_score = -1"),
            count("select count(*) from query_term_mappings"),
            count("select count(*) from query_term_mappings where enabled = true"),
            count("select count(*) from intent_routes"),
            count("select count(*) from intent_routes where enabled = true"),
            count("select count(*) from answer_prompt_templates"),
            count("select count(*) from answer_prompt_templates where enabled = true"),
            count("select count(*) from sample_prompts"),
            count("select count(*) from sample_prompts where enabled = true"),
            intValue("select coalesce(round(avg(latency_ms)), 0) from chat_records where latency_ms is not null"),
            count("select count(*) from rag_traces where status = 'FAILED'"),
            intValue("select coalesce(round(avg(retrieval_ms)), 0) from rag_traces"),
            intValue("select coalesce(round(avg(generation_ms)), 0) from rag_traces"),
            intValue("select coalesce(round(avg(answer_quality_score)), 0) from rag_traces where status = 'SUCCESS' and answer_quality_label <> 'UNASSESSED'"),
            count("select count(*) from parse_jobs"),
            count("select count(*) from parse_jobs where status = 'FAILED'"),
            intValue("select coalesce(round(avg(duration_ms)), 0) from parse_jobs where finished_at is not null"),
            processStatuses(),
            modelUsage(),
            modelHealth(),
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

    @Transactional(readOnly = true)
    public List<QueryTermMappingResponse> queryTermMappings(User currentUser) {
        requireAdmin(currentUser);
        return queryTermMappingRepository.findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(this::queryTermMappingResponse)
            .toList();
    }

    @Transactional
    public QueryTermMappingResponse createQueryTermMapping(QueryTermMappingRequest request, User currentUser) {
        requireAdmin(currentUser);
        String term = compact(request.term(), 120);
        String expansions = compact(request.expansions(), 1000);
        if (term == null || expansions == null) {
            throw new BusinessException("术语和扩展词不能为空");
        }
        queryTermMappingRepository.findByTermIgnoreCase(term).ifPresent(existing -> {
            throw new BusinessException("术语映射已存在");
        });
        QueryTermMapping mapping = new QueryTermMapping();
        mapping.setTerm(term);
        mapping.setExpansions(expansions);
        mapping.setEnabled(request.enabled() == null || request.enabled());
        return queryTermMappingResponse(queryTermMappingRepository.save(mapping));
    }

    @Transactional
    public QueryTermMappingResponse updateQueryTermMapping(Long id, QueryTermMappingRequest request, User currentUser) {
        requireAdmin(currentUser);
        QueryTermMapping mapping = queryTermMappingRepository.findById(id)
            .orElseThrow(() -> new BusinessException("术语映射不存在"));
        String term = compact(request.term(), 120);
        String expansions = compact(request.expansions(), 1000);
        if (term == null || expansions == null) {
            throw new BusinessException("术语和扩展词不能为空");
        }
        queryTermMappingRepository.findByTermIgnoreCase(term)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException("术语映射已存在");
            });
        mapping.setTerm(term);
        mapping.setExpansions(expansions);
        mapping.setEnabled(request.enabled() == null || request.enabled());
        return queryTermMappingResponse(queryTermMappingRepository.save(mapping));
    }

    @Transactional
    public void deleteQueryTermMapping(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!queryTermMappingRepository.existsById(id)) {
            throw new BusinessException("术语映射不存在");
        }
        queryTermMappingRepository.deleteById(id);
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

    private List<ModelHealthResponse> modelHealth() {
        return jdbcTemplate.query("""
            select
              m.provider,
              m.model_name,
              m.target_name,
              count(*) as total_calls,
              sum(case when m.status = 'SUCCESS' then 1 else 0 end) as success_calls,
              sum(case when m.status = 'FAILED' then 1 else 0 end) as failed_calls,
              sum(case when m.status = 'FALLBACK' then 1 else 0 end) as fallback_calls,
              coalesce(round(avg(case when m.status = 'SUCCESS' then m.latency_ms end)), 0) as average_latency_ms,
              (
                select latest.status
                from model_invocations latest
                where latest.target_name = m.target_name
                order by latest.created_at desc
                limit 1
              ) as last_status,
              max(m.created_at) as last_seen_at
            from model_invocations m
            group by m.provider, m.model_name, m.target_name
            order by max(m.created_at) desc
            limit 8
            """, (rs, rowNum) -> new ModelHealthResponse(
            rs.getString("provider"),
            rs.getString("model_name"),
            rs.getString("target_name"),
            rs.getString("last_status"),
            rs.getLong("total_calls"),
            rs.getLong("success_calls"),
            rs.getLong("failed_calls"),
            rs.getLong("fallback_calls"),
            rs.getInt("average_latency_ms"),
            offsetDateTime(rs, "last_seen_at")
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
              t.pipeline_name,
              t.query_intent,
              t.search_query,
              t.query_expansions_json::text as query_expansions_json,
              t.comparison_requested,
              t.answer_strategy,
              t.answer_contract,
              t.source_count,
              t.retrieval_ms,
              t.generation_ms,
              t.verification_ms,
              t.formatting_ms,
              t.evaluation_ms,
              t.answer_quality_score,
              t.answer_quality_label,
              t.answer_quality_notes,
              t.total_ms,
              t.error_message,
              t.retrieval_channels_json::text as retrieval_channels_json,
              t.retrieval_processors_json::text as retrieval_processors_json,
              t.node_spans_json::text as node_spans_json,
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
            rs.getString("pipeline_name"),
            rs.getString("query_intent"),
            rs.getString("search_query"),
            queryExpansions(rs.getString("query_expansions_json")),
            rs.getBoolean("comparison_requested"),
            rs.getString("answer_strategy"),
            rs.getString("answer_contract"),
            rs.getInt("source_count"),
            rs.getInt("retrieval_ms"),
            rs.getInt("generation_ms"),
            rs.getInt("verification_ms"),
            rs.getInt("formatting_ms"),
            rs.getInt("evaluation_ms"),
            rs.getInt("answer_quality_score"),
            rs.getString("answer_quality_label"),
            rs.getString("answer_quality_notes"),
            rs.getInt("total_ms"),
            rs.getString("error_message"),
            retrievalChannels(rs.getString("retrieval_channels_json")),
            retrievalProcessors(rs.getString("retrieval_processors_json")),
            nodeSpans(rs.getString("node_spans_json")),
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
              j.node_spans_json::text as node_spans_json,
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
            parseJobNodeSpans(rs.getString("node_spans_json")),
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

    private List<RagTraceNodeSpanResponse> nodeSpans(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<RagTraceRetrievalChannelResponse> retrievalChannels(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<RagTraceRetrievalProcessorResponse> retrievalProcessors(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<QueryExpansionResponse> queryExpansions(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ParseJobNodeSpanResponse> parseJobNodeSpans(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private QueryTermMappingResponse queryTermMappingResponse(QueryTermMapping mapping) {
        return new QueryTermMappingResponse(
            mapping.getId(),
            mapping.getTerm(),
            mapping.getExpansions(),
            Boolean.TRUE.equals(mapping.getEnabled()),
            mapping.getCreatedAt(),
            mapping.getUpdatedAt()
        );
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
