package com.frostwane.paperagent.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentToolResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentPipelineNodeResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminChunkResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ChatRateLimitResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.GuidanceResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.IngestionPipelineNodeResponse;
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
import com.frostwane.paperagent.admin.dto.AdminDtos.RetrievalChannelCatalogResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RetrievalProcessorCatalogResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.StatusCountResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ToolExecutionResponse;
import com.frostwane.paperagent.agent.limit.AgentRateLimitStatus;
import com.frostwane.paperagent.agent.limit.AgentRateLimiterService;
import com.frostwane.paperagent.agent.model.ModelCircuitBreaker;
import com.frostwane.paperagent.agent.pipeline.AgentNode;
import com.frostwane.paperagent.agent.pipeline.AgentNodeType;
import com.frostwane.paperagent.agent.pipeline.AgentPipeline;
import com.frostwane.paperagent.agent.retrieval.RetrievalChannel;
import com.frostwane.paperagent.agent.retrieval.RetrievalPostProcessor;
import com.frostwane.paperagent.agent.term.QueryTermMapping;
import com.frostwane.paperagent.agent.term.QueryTermMappingRepository;
import com.frostwane.paperagent.agent.tool.AgentTool;
import com.frostwane.paperagent.agent.tool.AgentToolRegistry;
import com.frostwane.paperagent.agent.tool.AgentToolSettingService;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.parse.IngestionPipelineCatalog;
import com.frostwane.paperagent.parse.IngestionPipelineCatalog.IngestionPipelineNodeDefinition;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final QueryTermMappingRepository queryTermMappingRepository;
    private final AgentRateLimiterService agentRateLimiterService;
    private final ModelCircuitBreaker modelCircuitBreaker;
    private final AgentToolRegistry agentToolRegistry;
    private final AgentToolSettingService agentToolSettingService;
    private final AgentPipeline agentPipeline;
    private final IngestionPipelineCatalog ingestionPipelineCatalog;
    private final List<RetrievalChannel> retrievalChannels;
    private final List<RetrievalPostProcessor> retrievalPostProcessors;
    private final ObjectMapper objectMapper;

    public AdminService(
        JdbcTemplate jdbcTemplate,
        UserRepository userRepository,
        QueryTermMappingRepository queryTermMappingRepository,
        AgentRateLimiterService agentRateLimiterService,
        ModelCircuitBreaker modelCircuitBreaker,
        AgentToolRegistry agentToolRegistry,
        AgentToolSettingService agentToolSettingService,
        AgentPipeline agentPipeline,
        IngestionPipelineCatalog ingestionPipelineCatalog,
        List<RetrievalChannel> retrievalChannels,
        List<RetrievalPostProcessor> retrievalPostProcessors,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.queryTermMappingRepository = queryTermMappingRepository;
        this.agentRateLimiterService = agentRateLimiterService;
        this.modelCircuitBreaker = modelCircuitBreaker;
        this.agentToolRegistry = agentToolRegistry;
        this.agentToolSettingService = agentToolSettingService;
        this.agentPipeline = agentPipeline;
        this.ingestionPipelineCatalog = ingestionPipelineCatalog;
        this.retrievalChannels = retrievalChannels.stream()
            .sorted(Comparator.comparingInt(RetrievalChannel::priority))
            .toList();
        this.retrievalPostProcessors = retrievalPostProcessors.stream()
            .sorted(Comparator.comparingInt(RetrievalPostProcessor::order))
            .toList();
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
            count("select count(*) from model_targets"),
            count("select count(*) from model_targets where enabled = true"),
            count("select count(*) from sample_prompts"),
            count("select count(*) from sample_prompts where enabled = true"),
            intValue("select coalesce(round(avg(latency_ms)), 0) from chat_records where latency_ms is not null"),
            count("select count(*) from rag_traces where status = 'FAILED'"),
            chatRateLimit(),
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

    private ChatRateLimitResponse chatRateLimit() {
        AgentRateLimitStatus status = agentRateLimiterService.status();
        return new ChatRateLimitResponse(
            status.enabled(),
            status.activeGlobal(),
            status.activeUsers(),
            status.recentRequests(),
            status.globalConcurrencyLimit(),
            status.userConcurrencyLimit(),
            status.userPerMinuteLimit()
        );
    }

    private AgentToolResponse agentToolResponse(AgentTool tool, AgentToolStats stats, boolean enabled) {
        AgentToolStats safeStats = stats == null ? AgentToolStats.empty() : stats;
        return new AgentToolResponse(
            tool.name(),
            tool.label(),
            tool.description(),
            tool.triggerDescription(),
            "INTERNAL",
            enabled,
            safeStats.totalCalls(),
            safeStats.successCalls(),
            safeStats.failedCalls(),
            safeStats.averageLatencyMs(),
            safeStats.lastSeenAt()
        );
    }

    private Map<String, AgentToolStats> agentToolStats() {
        Map<String, AgentToolStats> stats = new HashMap<>();
        jdbcTemplate.query("""
            select
              tool.item->>'name' as name,
              count(*) as total_calls,
              sum(case when tool.item->>'status' = 'SUCCESS' then 1 else 0 end) as success_calls,
              sum(case when tool.item->>'status' = 'FAILED' then 1 else 0 end) as failed_calls,
              coalesce(round(avg(
                case
                  when jsonb_typeof(tool.item->'latencyMs') = 'number'
                  then (tool.item->>'latencyMs')::numeric
                  else null
                end
              )), 0) as average_latency_ms,
              max(t.created_at) as last_seen_at
            from rag_traces t
            cross join lateral jsonb_array_elements(coalesce(t.tool_executions_json, '[]'::jsonb)) as tool(item)
            where coalesce(tool.item->>'name', '') <> ''
            group by tool.item->>'name'
            """, rs -> {
            while (rs.next()) {
                stats.put(rs.getString("name"), new AgentToolStats(
                    rs.getLong("total_calls"),
                    rs.getLong("success_calls"),
                    rs.getLong("failed_calls"),
                    rs.getInt("average_latency_ms"),
                    offsetDateTime(rs, "last_seen_at")
                ));
            }
        });
        return stats;
    }

    private AgentPipelineNodeResponse agentPipelineNodeResponse(AgentNode node, AgentNodeStats stats) {
        AgentNodeStats safeStats = stats == null ? AgentNodeStats.empty() : stats;
        return new AgentPipelineNodeResponse(
            agentPipeline.name(),
            node.type().name(),
            node.name(),
            nodeLabel(node.type()),
            nodeDescription(node.type()),
            node.order(),
            true,
            safeStats.totalRuns(),
            safeStats.successRuns(),
            safeStats.failedRuns(),
            safeStats.averageLatencyMs(),
            safeStats.lastSeenAt()
        );
    }

    private Map<String, AgentNodeStats> agentNodeStats() {
        Map<String, AgentNodeStats> stats = new HashMap<>();
        jdbcTemplate.query("""
            select
              coalesce(node.item->>'type', '') as type,
              coalesce(node.item->>'name', '') as name,
              count(*) as total_runs,
              sum(case when node.item->>'status' = 'SUCCESS' then 1 else 0 end) as success_runs,
              sum(case when node.item->>'status' = 'FAILED' then 1 else 0 end) as failed_runs,
              coalesce(round(avg(
                case
                  when jsonb_typeof(node.item->'durationMs') = 'number'
                  then (node.item->>'durationMs')::numeric
                  else null
                end
              )), 0) as average_latency_ms,
              max(t.created_at) as last_seen_at
            from rag_traces t
            cross join lateral jsonb_array_elements(coalesce(t.node_spans_json, '[]'::jsonb)) as node(item)
            where coalesce(node.item->>'type', '') <> ''
            group by coalesce(node.item->>'type', ''), coalesce(node.item->>'name', '')
            """, rs -> {
            while (rs.next()) {
                stats.put(agentNodeKey(rs.getString("type"), rs.getString("name")), new AgentNodeStats(
                    rs.getLong("total_runs"),
                    rs.getLong("success_runs"),
                    rs.getLong("failed_runs"),
                    rs.getInt("average_latency_ms"),
                    offsetDateTime(rs, "last_seen_at")
                ));
            }
        });
        return stats;
    }

    private String agentNodeKey(AgentNode node) {
        return agentNodeKey(node.type().name(), node.name());
    }

    private String agentNodeKey(String type, String name) {
        return defaultText(type, "") + "::" + defaultText(name, "");
    }

    private String nodeLabel(AgentNodeType type) {
        return switch (type) {
            case SCOPE_RESOLUTION -> "范围解析";
            case MEMORY -> "会话记忆";
            case QUERY_REWRITE -> "问题改写";
            case QUERY_PLANNING -> "查询规划";
            case TOOL_EXECUTION -> "工具执行";
            case RETRIEVAL -> "知识检索";
            case INTENT_GUIDANCE -> "意图引导";
            case ANSWER_PLANNING -> "回答规划";
            case GENERATION -> "回答生成";
            case VERIFICATION -> "引用校验";
            case EVALUATION -> "质量评估";
            case FORMATTING -> "格式整理";
        };
    }

    private String nodeDescription(AgentNodeType type) {
        return switch (type) {
            case SCOPE_RESOLUTION -> "解析单篇或全库问答范围，并校验文献归属。";
            case MEMORY -> "读取近期会话和长期摘要，压缩成可注入 Prompt 的上下文。";
            case QUERY_REWRITE -> "按配置改写问题并拆分子问题，提升检索召回。";
            case QUERY_PLANNING -> "识别问题意图、生成检索式，并标记跨文献比较需求。";
            case TOOL_EXECUTION -> "匹配内部业务工具，把统计或运营类结果注入回答上下文。";
            case RETRIEVAL -> "执行多通道检索和后处理，产出最终证据片段。";
            case INTENT_GUIDANCE -> "在问题过泛或证据不足时生成澄清说明和建议追问。";
            case ANSWER_PLANNING -> "根据意图、工具、证据和引导状态选择回答策略与输出契约。";
            case GENERATION -> "调用回答 Agent，基于证据、记忆和模板生成 Markdown 回答。";
            case VERIFICATION -> "检查引用页码和材料不足提示，降低无依据回答风险。";
            case EVALUATION -> "生成启发式或模型评审质量信号，写入 Trace。";
            case FORMATTING -> "统一最终回答格式，补齐可读性和结构。";
        };
    }

    private IngestionPipelineNodeResponse ingestionPipelineNodeResponse(IngestionPipelineNodeDefinition node, IngestionNodeStats stats) {
        IngestionNodeStats safeStats = stats == null ? IngestionNodeStats.empty() : stats;
        return new IngestionPipelineNodeResponse(
            IngestionPipelineCatalog.PIPELINE_NAME,
            node.type(),
            node.name(),
            node.label(),
            node.description(),
            node.order(),
            node.enabled(),
            safeStats.totalRuns(),
            safeStats.successRuns(),
            safeStats.failedRuns(),
            safeStats.averageLatencyMs(),
            safeStats.lastSeenAt()
        );
    }

    private Map<String, IngestionNodeStats> ingestionNodeStats() {
        Map<String, IngestionNodeStats> stats = new HashMap<>();
        jdbcTemplate.query("""
            select
              coalesce(node.item->>'type', '') as type,
              coalesce(node.item->>'name', '') as name,
              count(*) as total_runs,
              sum(case when node.item->>'status' = 'SUCCESS' then 1 else 0 end) as success_runs,
              sum(case when node.item->>'status' = 'FAILED' then 1 else 0 end) as failed_runs,
              coalesce(round(avg(
                case
                  when jsonb_typeof(node.item->'durationMs') = 'number'
                  then (node.item->>'durationMs')::numeric
                  else null
                end
              )), 0) as average_latency_ms,
              max(j.started_at) as last_seen_at
            from parse_jobs j
            cross join lateral jsonb_array_elements(coalesce(j.node_spans_json, '[]'::jsonb)) as node(item)
            where coalesce(node.item->>'type', '') <> ''
            group by coalesce(node.item->>'type', ''), coalesce(node.item->>'name', '')
            """, rs -> {
            while (rs.next()) {
                stats.put(ingestionNodeKey(rs.getString("type"), rs.getString("name")), new IngestionNodeStats(
                    rs.getLong("total_runs"),
                    rs.getLong("success_runs"),
                    rs.getLong("failed_runs"),
                    rs.getInt("average_latency_ms"),
                    offsetDateTime(rs, "last_seen_at")
                ));
            }
        });
        return stats;
    }

    private String ingestionNodeKey(IngestionPipelineNodeDefinition node) {
        return ingestionNodeKey(node.type(), node.name());
    }

    private String ingestionNodeKey(String type, String name) {
        return defaultText(type, "") + "::" + defaultText(name, "");
    }

    private RetrievalChannelCatalogResponse retrievalChannelResponse(RetrievalChannel channel, RetrievalComponentStats stats) {
        RetrievalComponentStats safeStats = stats == null ? RetrievalComponentStats.empty() : stats;
        return new RetrievalChannelCatalogResponse(
            channel.name(),
            channel.label(),
            retrievalChannelDescription(channel.name()),
            channel.priority(),
            true,
            safeStats.totalRuns(),
            safeStats.successRuns(),
            safeStats.failedRuns(),
            safeStats.totalOutputCount(),
            safeStats.averageOutputCount(),
            safeStats.averageLatencyMs(),
            safeStats.lastSeenAt()
        );
    }

    private RetrievalProcessorCatalogResponse retrievalProcessorResponse(RetrievalPostProcessor processor, RetrievalComponentStats stats) {
        RetrievalComponentStats safeStats = stats == null ? RetrievalComponentStats.empty() : stats;
        return new RetrievalProcessorCatalogResponse(
            processor.name(),
            processor.label(),
            retrievalProcessorDescription(processor.name()),
            processor.order(),
            true,
            safeStats.totalRuns(),
            safeStats.successRuns(),
            safeStats.failedRuns(),
            safeStats.averageInputCount(),
            safeStats.averageOutputCount(),
            safeStats.averageLatencyMs(),
            safeStats.lastSeenAt()
        );
    }

    private Map<String, RetrievalComponentStats> retrievalChannelStats() {
        Map<String, RetrievalComponentStats> stats = new HashMap<>();
        jdbcTemplate.query("""
            select
              channel.item->>'name' as name,
              count(*) as total_runs,
              sum(case when channel.item->>'status' = 'SUCCESS' then 1 else 0 end) as success_runs,
              sum(case when channel.item->>'status' = 'FAILED' then 1 else 0 end) as failed_runs,
              coalesce(sum(
                case
                  when jsonb_typeof(channel.item->'candidateCount') = 'number'
                  then (channel.item->>'candidateCount')::numeric
                  else 0
                end
              ), 0) as total_output_count,
              coalesce(round(avg(
                case
                  when jsonb_typeof(channel.item->'candidateCount') = 'number'
                  then (channel.item->>'candidateCount')::numeric
                  else null
                end
              )), 0) as average_output_count,
              coalesce(round(avg(
                case
                  when jsonb_typeof(channel.item->'latencyMs') = 'number'
                  then (channel.item->>'latencyMs')::numeric
                  else null
                end
              )), 0) as average_latency_ms,
              max(t.created_at) as last_seen_at
            from rag_traces t
            cross join lateral jsonb_array_elements(coalesce(t.retrieval_channels_json, '[]'::jsonb)) as channel(item)
            where coalesce(channel.item->>'name', '') <> ''
            group by channel.item->>'name'
            """, rs -> {
            while (rs.next()) {
                stats.put(rs.getString("name"), new RetrievalComponentStats(
                    rs.getLong("total_runs"),
                    rs.getLong("success_runs"),
                    rs.getLong("failed_runs"),
                    0,
                    rs.getInt("average_output_count"),
                    rs.getLong("total_output_count"),
                    rs.getInt("average_latency_ms"),
                    offsetDateTime(rs, "last_seen_at")
                ));
            }
        });
        return stats;
    }

    private Map<String, RetrievalComponentStats> retrievalProcessorStats() {
        Map<String, RetrievalComponentStats> stats = new HashMap<>();
        jdbcTemplate.query("""
            select
              processor.item->>'name' as name,
              count(*) as total_runs,
              sum(case when processor.item->>'status' = 'SUCCESS' then 1 else 0 end) as success_runs,
              sum(case when processor.item->>'status' = 'FAILED' then 1 else 0 end) as failed_runs,
              coalesce(round(avg(
                case
                  when jsonb_typeof(processor.item->'inputCount') = 'number'
                  then (processor.item->>'inputCount')::numeric
                  else null
                end
              )), 0) as average_input_count,
              coalesce(round(avg(
                case
                  when jsonb_typeof(processor.item->'outputCount') = 'number'
                  then (processor.item->>'outputCount')::numeric
                  else null
                end
              )), 0) as average_output_count,
              coalesce(sum(
                case
                  when jsonb_typeof(processor.item->'outputCount') = 'number'
                  then (processor.item->>'outputCount')::numeric
                  else 0
                end
              ), 0) as total_output_count,
              coalesce(round(avg(
                case
                  when jsonb_typeof(processor.item->'latencyMs') = 'number'
                  then (processor.item->>'latencyMs')::numeric
                  else null
                end
              )), 0) as average_latency_ms,
              max(t.created_at) as last_seen_at
            from rag_traces t
            cross join lateral jsonb_array_elements(coalesce(t.retrieval_processors_json, '[]'::jsonb)) as processor(item)
            where coalesce(processor.item->>'name', '') <> ''
            group by processor.item->>'name'
            """, rs -> {
            while (rs.next()) {
                stats.put(rs.getString("name"), new RetrievalComponentStats(
                    rs.getLong("total_runs"),
                    rs.getLong("success_runs"),
                    rs.getLong("failed_runs"),
                    rs.getInt("average_input_count"),
                    rs.getInt("average_output_count"),
                    rs.getLong("total_output_count"),
                    rs.getInt("average_latency_ms"),
                    offsetDateTime(rs, "last_seen_at")
                ));
            }
        });
        return stats;
    }

    private String retrievalChannelDescription(String name) {
        return switch (defaultText(name, "")) {
            case "vector" -> "基于 chunk embedding 执行 pgvector 相似度召回，只检索启用片段。";
            case "keyword" -> "在启用 chunk 正文中执行轻量关键词匹配，作为精确词和中文问法兜底。";
            default -> "注册在 Spring Bean 中的检索通道。";
        };
    }

    private String retrievalProcessorDescription(String name) {
        return switch (defaultText(name, "")) {
            case "channel-fusion" -> "合并多通道候选，按通道权重融合排序。";
            case "query-aware-rerank" -> "根据检索式命中词对候选进行规则精排。";
            case "model-rerank" -> "按配置调用 RETRIEVAL_RERANK 模型重排前 N 个候选。";
            case "paper-diversity" -> "全库场景提升跨论文多样性，避免单篇论文垄断来源。";
            case "result-limit" -> "按最终来源上限截断候选，控制回答上下文规模。";
            default -> "注册在 Spring Bean 中的检索后处理器。";
        };
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

    @Transactional(readOnly = true)
    public List<AgentToolResponse> agentTools(User currentUser) {
        requireAdmin(currentUser);
        Map<String, AgentToolStats> stats = agentToolStats();
        Map<String, Boolean> enabledByToolName = agentToolSettingService.enabledByToolName();
        return agentToolRegistry.tools().stream()
            .map(tool -> agentToolResponse(tool, stats.get(tool.name()), enabledByToolName.getOrDefault(tool.name(), true)))
            .toList();
    }

    @Transactional
    public AgentToolResponse updateAgentToolEnabled(String name, boolean enabled, User currentUser) {
        requireAdmin(currentUser);
        AgentTool tool = agentToolRegistry.tools().stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new BusinessException("Agent 工具不存在"));
        boolean updated = agentToolSettingService.updateEnabled(tool.name(), enabled);
        return agentToolResponse(tool, agentToolStats().get(tool.name()), updated);
    }

    @Transactional(readOnly = true)
    public List<AgentPipelineNodeResponse> agentPipelineNodes(User currentUser) {
        requireAdmin(currentUser);
        Map<String, AgentNodeStats> stats = agentNodeStats();
        return agentPipeline.nodes().stream()
            .map(node -> agentPipelineNodeResponse(node, stats.get(agentNodeKey(node))))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<IngestionPipelineNodeResponse> ingestionPipelineNodes(User currentUser) {
        requireAdmin(currentUser);
        Map<String, IngestionNodeStats> stats = ingestionNodeStats();
        return ingestionPipelineCatalog.nodes().stream()
            .map(node -> ingestionPipelineNodeResponse(node, stats.get(ingestionNodeKey(node))))
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ParseJobResponse> parseJobs(
        User currentUser,
        String status,
        String keyword,
        int page,
        int pageSize
    ) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(80, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<Object> params = new ArrayList<>();
        String where = parseJobWhere(status, keyword, params);
        long total = numberValue("select count(*) " + parseJobFromSql() + where, params);
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(safePageSize);
        rowParams.add(offset);
        List<ParseJobResponse> items = jdbcTemplate.query(
            parseJobSelectSql() + where + " order by j.started_at desc, j.id desc limit ? offset ?",
            this::mapParseJob,
            rowParams.toArray()
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safePageSize);
        return new PageResponse<>(items, total, safePage, safePageSize, totalPages);
    }

    @Transactional(readOnly = true)
    public List<RetrievalChannelCatalogResponse> retrievalChannels(User currentUser) {
        requireAdmin(currentUser);
        Map<String, RetrievalComponentStats> stats = retrievalChannelStats();
        return retrievalChannels.stream()
            .map(channel -> retrievalChannelResponse(channel, stats.get(channel.name())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RetrievalProcessorCatalogResponse> retrievalProcessors(User currentUser) {
        requireAdmin(currentUser);
        Map<String, RetrievalComponentStats> stats = retrievalProcessorStats();
        return retrievalPostProcessors.stream()
            .map(processor -> retrievalProcessorResponse(processor, stats.get(processor.name())))
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminChunkResponse> chunks(
        User currentUser,
        Long paperId,
        String keyword,
        int page,
        int pageSize
    ) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(80, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<Object> params = new ArrayList<>();
        String where = chunkWhere(paperId, keyword, params);
        long total = numberValue("select count(*) " + chunkFromSql() + where, params);
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(safePageSize);
        rowParams.add(offset);
        List<AdminChunkResponse> items = jdbcTemplate.query(
            chunkSelectSql() + where + " order by c.created_at desc, c.id desc limit ? offset ?",
            this::mapChunk,
            rowParams.toArray()
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safePageSize);
        return new PageResponse<>(items, total, safePage, safePageSize, totalPages);
    }

    @Transactional
    public AdminChunkResponse updateChunkEnabled(Long chunkId, boolean enabled, User currentUser) {
        requireAdmin(currentUser);
        int updated = jdbcTemplate.update("update paper_chunks set enabled = ? where id = ?", enabled, chunkId);
        if (updated == 0) {
            throw new BusinessException("知识片段不存在");
        }
        return jdbcTemplate.query(
                chunkSelectSql() + " where c.id = ?",
                this::mapChunk,
                chunkId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("知识片段不存在"));
    }

    @Transactional(readOnly = true)
    public PageResponse<RagTraceResponse> ragTraces(
        User currentUser,
        String status,
        String scope,
        Long sessionId,
        String keyword,
        int page,
        int pageSize
    ) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(80, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<Object> params = new ArrayList<>();
        String where = traceWhere(status, scope, sessionId, keyword, params);
        long total = numberValue("select count(*) " + traceFromSql() + where, params);
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(safePageSize);
        rowParams.add(offset);
        List<RagTraceResponse> items = jdbcTemplate.query(
            traceSelectSql() + where + " order by t.created_at desc limit ? offset ?",
            this::mapTrace,
            rowParams.toArray()
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safePageSize);
        return new PageResponse<>(items, total, safePage, safePageSize, totalPages);
    }

    @Transactional(readOnly = true)
    public RagTraceResponse ragTrace(Long id, User currentUser) {
        requireAdmin(currentUser);
        return jdbcTemplate.query(
                traceSelectSql() + " where t.id = ?",
                this::mapTrace,
                id
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("Trace 不存在"));
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
              m.task_type,
              m.provider,
              m.model_name,
              m.target_name,
              count(*) as total_calls,
              sum(case when m.status = 'SUCCESS' then 1 else 0 end) as success_calls,
              sum(case when m.status = 'FAILED' then 1 else 0 end) as failed_calls,
              sum(case when m.status = 'FALLBACK' then 1 else 0 end) as fallback_calls,
              sum(case when m.status = 'SKIPPED' then 1 else 0 end) as skipped_calls,
              coalesce(round(avg(case when m.status = 'SUCCESS' then m.latency_ms end)), 0) as average_latency_ms,
              (
                select latest.status
                from model_invocations latest
                where latest.target_name = m.target_name
                  and latest.task_type = m.task_type
                order by latest.created_at desc
                limit 1
              ) as last_status,
              max(m.created_at) as last_seen_at
            from model_invocations m
            group by m.task_type, m.provider, m.model_name, m.target_name
            order by max(m.created_at) desc
            limit 8
            """, (rs, rowNum) -> {
            ModelCircuitBreaker.CircuitSnapshot circuit = modelCircuitBreaker.snapshot(rs.getString("target_name"));
            return new ModelHealthResponse(
                rs.getString("task_type"),
                rs.getString("provider"),
                rs.getString("model_name"),
                rs.getString("target_name"),
                rs.getString("last_status"),
                rs.getLong("total_calls"),
                rs.getLong("success_calls"),
                rs.getLong("failed_calls"),
                rs.getLong("fallback_calls"),
                rs.getLong("skipped_calls"),
                circuit.state(),
                circuit.consecutiveFailures(),
                circuit.openUntil(),
                rs.getInt("average_latency_ms"),
                offsetDateTime(rs, "last_seen_at")
            );
        });
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
        return jdbcTemplate.query(traceSelectSql() + " order by t.created_at desc limit 8", this::mapTrace);
    }

    private String chunkSelectSql() {
        return """
            select
              c.id,
              u.username,
              p.id as paper_id,
              p.title as paper_title,
              c.page_number,
              c.chunk_index,
              case
                when length(c.content) > 1200 then substring(c.content from 1 for 1200) || '...'
                else c.content
              end as content_preview,
              length(c.content) as content_length,
              c.embedding is not null as embedded,
              c.enabled,
              c.created_at
            """ + chunkFromSql();
    }

    private String chunkFromSql() {
        return """
            from paper_chunks c
            join papers p on p.id = c.paper_id
            join users u on u.id = p.owner_id
            """;
    }

    private String chunkWhere(Long paperId, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (paperId != null && paperId > 0) {
            where.append(" and p.id = ?");
            params.add(paperId);
        }
        String normalizedKeyword = compact(keyword, 160);
        if (normalizedKeyword != null) {
            String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
            where.append("""
                 and (
                   lower(c.content) like ?
                   or lower(p.title) like ?
                   or lower(coalesce(p.authors, '')) like ?
                   or lower(coalesce(p.keywords, '')) like ?
                   or lower(u.username) like ?
                 )
                """);
            for (int i = 0; i < 5; i++) {
                params.add(pattern);
            }
        }
        return where.toString();
    }

    private AdminChunkResponse mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new AdminChunkResponse(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getLong("paper_id"),
            rs.getString("paper_title"),
            rs.getInt("page_number"),
            rs.getInt("chunk_index"),
            rs.getString("content_preview"),
            rs.getInt("content_length"),
            rs.getBoolean("embedded"),
            rs.getBoolean("enabled"),
            offsetDateTime(rs, "created_at")
        );
    }

    private String traceSelectSql() {
        return """
            select
              t.id,
              u.username,
              p.id as paper_id,
              p.title as paper_title,
              s.id as session_id,
              s.title as session_title,
              t.scope,
              t.question,
              t.status,
              coalesce(t.model_name, 'fallback') as model_name,
              t.pipeline_name,
              t.query_intent,
              t.search_query,
              t.rewritten_query,
              t.query_sub_questions_json::text as query_sub_questions_json,
              t.query_rewrite_enabled,
              t.query_rewrite_model_name,
              t.query_expansions_json::text as query_expansions_json,
              t.tool_executions_json::text as tool_executions_json,
              t.guidance_json::text as guidance_json,
              t.comparison_requested,
              t.answer_strategy,
              t.answer_contract,
              t.source_count,
              t.memory_turn_count,
              t.memory_chars,
              t.memory_summary_used,
              t.memory_summary_turn_count,
              t.memory_summary_chars,
              t.memory_summary_method,
              t.memory_summary_model_name,
              t.retrieval_ms,
              t.generation_ms,
              t.verification_ms,
              t.formatting_ms,
              t.evaluation_ms,
              t.answer_quality_score,
              t.answer_quality_label,
              t.answer_quality_notes,
              t.answer_quality_method,
              t.answer_quality_judge_enabled,
              t.answer_quality_judge_model_name,
              t.answer_quality_confidence,
              t.total_ms,
              t.error_message,
              t.retrieval_channels_json::text as retrieval_channels_json,
              t.retrieval_processors_json::text as retrieval_processors_json,
              t.node_spans_json::text as node_spans_json,
              t.created_at
            """ + traceFromSql();
    }

    private String traceFromSql() {
        return """
            from rag_traces t
            join users u on u.id = t.owner_id
            left join papers p on p.id = t.paper_id
            left join chat_sessions s on s.id = t.session_id
            """;
    }

    private String traceWhere(String status, String scope, Long sessionId, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        String normalizedStatus = compact(status, 32);
        if (normalizedStatus != null) {
            where.append(" and upper(t.status) = ?");
            params.add(normalizedStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedScope = compact(scope, 32);
        if (normalizedScope != null) {
            where.append(" and upper(t.scope) = ?");
            params.add(normalizedScope.toUpperCase(Locale.ROOT));
        }
        if (sessionId != null && sessionId > 0) {
            where.append(" and t.session_id = ?");
            params.add(sessionId);
        }
        String normalizedKeyword = compact(keyword, 160);
        if (normalizedKeyword != null) {
            String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
            where.append("""
                 and (
                   lower(t.question) like ?
                   or lower(coalesce(t.search_query, '')) like ?
                   or lower(coalesce(t.rewritten_query, '')) like ?
                   or lower(coalesce(t.query_intent, '')) like ?
                   or lower(coalesce(t.model_name, 'fallback')) like ?
                   or lower(coalesce(t.tool_executions_json::text, '[]')) like ?
                   or lower(coalesce(t.guidance_json::text, '{}')) like ?
                   or lower(coalesce(t.error_message, '')) like ?
                   or lower(coalesce(s.title, '')) like ?
                   or lower(coalesce(p.title, '')) like ?
                   or lower(u.username) like ?
                 )
                """);
            for (int i = 0; i < 11; i++) {
                params.add(pattern);
            }
        }
        return where.toString();
    }

    private long numberValue(String sql, List<Object> params) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, params.toArray());
        return value == null ? 0L : value.longValue();
    }

    private RagTraceResponse mapTrace(ResultSet rs, int rowNum) throws SQLException {
        return new RagTraceResponse(
            rs.getLong("id"),
            rs.getString("username"),
            nullableLong(rs, "paper_id"),
            rs.getString("paper_title"),
            nullableLong(rs, "session_id"),
            rs.getString("session_title"),
            rs.getString("scope"),
            rs.getString("question"),
            rs.getString("status"),
            rs.getString("model_name"),
            rs.getString("pipeline_name"),
            rs.getString("query_intent"),
            rs.getString("search_query"),
            rs.getString("rewritten_query"),
            stringList(rs.getString("query_sub_questions_json")),
            rs.getBoolean("query_rewrite_enabled"),
            rs.getString("query_rewrite_model_name"),
            queryExpansions(rs.getString("query_expansions_json")),
            rs.getBoolean("comparison_requested"),
            rs.getString("answer_strategy"),
            rs.getString("answer_contract"),
            toolExecutions(rs.getString("tool_executions_json")),
            guidance(rs.getString("guidance_json")),
            rs.getInt("source_count"),
            rs.getInt("memory_turn_count"),
            rs.getInt("memory_chars"),
            rs.getBoolean("memory_summary_used"),
            rs.getInt("memory_summary_turn_count"),
            rs.getInt("memory_summary_chars"),
            rs.getString("memory_summary_method"),
            rs.getString("memory_summary_model_name"),
            rs.getInt("retrieval_ms"),
            rs.getInt("generation_ms"),
            rs.getInt("verification_ms"),
            rs.getInt("formatting_ms"),
            rs.getInt("evaluation_ms"),
            rs.getInt("answer_quality_score"),
            rs.getString("answer_quality_label"),
            rs.getString("answer_quality_notes"),
            rs.getString("answer_quality_method"),
            rs.getBoolean("answer_quality_judge_enabled"),
            rs.getString("answer_quality_judge_model_name"),
            rs.getInt("answer_quality_confidence"),
            rs.getInt("total_ms"),
            rs.getString("error_message"),
            retrievalChannels(rs.getString("retrieval_channels_json")),
            retrievalProcessors(rs.getString("retrieval_processors_json")),
            nodeSpans(rs.getString("node_spans_json")),
            offsetDateTime(rs, "created_at")
        );
    }

    private List<ParseJobResponse> recentParseJobs() {
        return jdbcTemplate.query(parseJobSelectSql() + " order by j.started_at desc, j.id desc limit 8", this::mapParseJob);
    }

    private String parseJobSelectSql() {
        return """
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
            """ + parseJobFromSql();
    }

    private String parseJobFromSql() {
        return """
            from parse_jobs j
            join users u on u.id = j.owner_id
            """;
    }

    private String parseJobWhere(String status, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        String normalizedStatus = compact(status, 32);
        if (normalizedStatus != null) {
            where.append(" and upper(j.status) = ?");
            params.add(normalizedStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedKeyword = compact(keyword, 160);
        if (normalizedKeyword != null) {
            String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
            where.append("""
                 and (
                   lower(coalesce(j.paper_title, '')) like ?
                   or lower(coalesce(j.file_name, '')) like ?
                   or lower(coalesce(j.error_message, '')) like ?
                   or lower(coalesce(j.node_spans_json::text, '[]')) like ?
                   or lower(u.username) like ?
                 )
                """);
            for (int i = 0; i < 5; i++) {
                params.add(pattern);
            }
        }
        return where.toString();
    }

    private ParseJobResponse mapParseJob(ResultSet rs, int rowNum) throws SQLException {
        return new ParseJobResponse(
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
        );
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

    private List<ToolExecutionResponse> toolExecutions(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private GuidanceResponse guidance(String json) {
        try {
            GuidanceResponse parsed = objectMapper.readValue(json == null ? "{}" : json, GuidanceResponse.class);
            return new GuidanceResponse(
                parsed.required(),
                defaultText(parsed.type(), "NONE"),
                parsed.message(),
                parsed.reason(),
                parsed.suggestions() == null ? List.of() : parsed.suggestions()
            );
        } catch (Exception ex) {
            return new GuidanceResponse(false, "NONE", null, null, List.of());
        }
    }

    private List<String> stringList(String json) {
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

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record AgentToolStats(
        long totalCalls,
        long successCalls,
        long failedCalls,
        int averageLatencyMs,
        OffsetDateTime lastSeenAt
    ) {
        static AgentToolStats empty() {
            return new AgentToolStats(0, 0, 0, 0, null);
        }
    }

    private record AgentNodeStats(
        long totalRuns,
        long successRuns,
        long failedRuns,
        int averageLatencyMs,
        OffsetDateTime lastSeenAt
    ) {
        static AgentNodeStats empty() {
            return new AgentNodeStats(0, 0, 0, 0, null);
        }
    }

    private record RetrievalComponentStats(
        long totalRuns,
        long successRuns,
        long failedRuns,
        int averageInputCount,
        int averageOutputCount,
        long totalOutputCount,
        int averageLatencyMs,
        OffsetDateTime lastSeenAt
    ) {
        static RetrievalComponentStats empty() {
            return new RetrievalComponentStats(0, 0, 0, 0, 0, 0, 0, null);
        }
    }

    private record IngestionNodeStats(
        long totalRuns,
        long successRuns,
        long failedRuns,
        int averageLatencyMs,
        OffsetDateTime lastSeenAt
    ) {
        static IngestionNodeStats empty() {
            return new IngestionNodeStats(0, 0, 0, 0, null);
        }
    }
}
