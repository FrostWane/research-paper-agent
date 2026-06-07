package com.frostwane.paperagent.admin;

import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminAuditLogResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminChunkResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminChunkEnabledRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentPipelineNodeEnabledRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentPipelineNodeResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentToolEnabledRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentToolExecutionAuditResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentToolMinimumRoleRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AgentToolResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AnswerPromptTemplateRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AnswerPromptTemplateResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseFromTraceRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseResultResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationDatasetRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationDatasetResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationRunRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationRunResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.IngestionPipelineNodeResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelCircuitResetResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelTargetRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelTargetResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.ParseJobResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagTraceResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RetrievalChannelCatalogResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RetrievalProcessorCatalogResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.UserStatusUpdateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamTaskResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptResponse;
import com.frostwane.paperagent.agent.intent.IntentRouteService;
import com.frostwane.paperagent.agent.evaluation.AgentEvaluationRunService;
import com.frostwane.paperagent.agent.evaluation.AgentEvaluationService;
import com.frostwane.paperagent.agent.model.ModelTargetService;
import com.frostwane.paperagent.agent.prompt.AnswerPromptTemplateService;
import com.frostwane.paperagent.agent.sample.SamplePromptService;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.user.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SamplePromptService samplePromptService;
    private final RagSettingsService ragSettingsService;
    private final IntentRouteService intentRouteService;
    private final AnswerPromptTemplateService answerPromptTemplateService;
    private final ModelTargetService modelTargetService;
    private final AgentEvaluationService agentEvaluationService;
    private final AgentEvaluationRunService agentEvaluationRunService;
    private final CurrentUserService currentUserService;

    public AdminController(
        AdminService adminService,
        SamplePromptService samplePromptService,
        RagSettingsService ragSettingsService,
        IntentRouteService intentRouteService,
        AnswerPromptTemplateService answerPromptTemplateService,
        ModelTargetService modelTargetService,
        AgentEvaluationService agentEvaluationService,
        AgentEvaluationRunService agentEvaluationRunService,
        CurrentUserService currentUserService
    ) {
        this.adminService = adminService;
        this.samplePromptService = samplePromptService;
        this.ragSettingsService = ragSettingsService;
        this.intentRouteService = intentRouteService;
        this.answerPromptTemplateService = answerPromptTemplateService;
        this.modelTargetService = modelTargetService;
        this.agentEvaluationService = agentEvaluationService;
        this.agentEvaluationRunService = agentEvaluationRunService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(adminService.overview(currentUserService.getRequiredUser()));
    }

    @PostMapping("/stream-tasks/{taskId}/cancel")
    public ApiResponse<ChatStreamTaskResponse> cancelStreamTask(@PathVariable String taskId) {
        User admin = currentAdmin();
        ChatStreamTaskResponse response = adminService.cancelStreamTask(taskId, admin);
        audit(admin, "CANCEL_STREAM_TASK", "STREAM_TASK", response.taskId(), "强制停止流式问答任务", detail(
            "phase", response.phase(),
            "ownerUsername", response.ownerUsername(),
            "question", response.question(),
            "paperId", response.paperId(),
            "sessionId", response.sessionId()
        ));
        return ApiResponse.ok(response);
    }

    @PostMapping("/model-circuits/reset")
    public ApiResponse<ModelCircuitResetResponse> resetModelCircuit(@RequestParam String targetName) {
        User admin = currentAdmin();
        ModelCircuitResetResponse response = adminService.resetModelCircuit(targetName, admin);
        audit(admin, "RESET_MODEL_CIRCUIT", "MODEL_TARGET", response.targetName(), "复位模型目标熔断状态", detail(
            "targetName", response.targetName(),
            "circuitState", response.circuitState(),
            "consecutiveFailures", response.consecutiveFailures(),
            "circuitOpenUntil", response.circuitOpenUntil()
        ));
        return ApiResponse.ok(response);
    }

    @GetMapping("/rag-traces")
    public ApiResponse<PageResponse<RagTraceResponse>> ragTraces(
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "") String scope,
        @RequestParam(required = false) Long sessionId,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(adminService.ragTraces(
            currentUserService.getRequiredUser(),
            status,
            scope,
            sessionId,
            keyword,
            page,
            pageSize
        ));
    }

    @GetMapping("/rag-traces/{id}")
    public ApiResponse<RagTraceResponse> ragTrace(@PathVariable Long id) {
        return ApiResponse.ok(adminService.ragTrace(id, currentUserService.getRequiredUser()));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> users() {
        return ApiResponse.ok(adminService.users(currentUserService.getRequiredUser()));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogResponse>> adminAuditLogs(
        @RequestParam(defaultValue = "") String action,
        @RequestParam(defaultValue = "") String resourceType,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(adminService.adminAuditLogs(
            currentUserService.getRequiredUser(),
            action,
            resourceType,
            keyword,
            page,
            pageSize
        ));
    }

    @GetMapping("/agent-tools")
    public ApiResponse<List<AgentToolResponse>> agentTools() {
        return ApiResponse.ok(adminService.agentTools(currentUserService.getRequiredUser()));
    }

    @GetMapping("/agent-tool-executions")
    public ApiResponse<PageResponse<AgentToolExecutionAuditResponse>> agentToolExecutions(
        @RequestParam(defaultValue = "") String toolName,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(adminService.agentToolExecutions(
            currentUserService.getRequiredUser(),
            toolName,
            status,
            keyword,
            page,
            pageSize
        ));
    }

    @PatchMapping("/agent-tools/{name}/enabled")
    public ApiResponse<AgentToolResponse> updateAgentToolEnabled(@PathVariable String name, @Valid @RequestBody AgentToolEnabledRequest request) {
        User admin = currentAdmin();
        AgentToolResponse response = adminService.updateAgentToolEnabled(
            name,
            request.enabled(),
            admin
        );
        audit(admin, "UPDATE_AGENT_TOOL_ENABLED", "AGENT_TOOL", response.name(), (response.enabled() ? "启用" : "停用") + " Agent 工具", detail(
            "name", response.name(),
            "label", response.label(),
            "enabled", response.enabled(),
            "minimumRole", response.minimumRole()
        ));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/agent-tools/{name}/minimum-role")
    public ApiResponse<AgentToolResponse> updateAgentToolMinimumRole(@PathVariable String name, @Valid @RequestBody AgentToolMinimumRoleRequest request) {
        User admin = currentAdmin();
        AgentToolResponse response = adminService.updateAgentToolMinimumRole(
            name,
            request.minimumRole(),
            admin
        );
        audit(admin, "UPDATE_AGENT_TOOL_ROLE", "AGENT_TOOL", response.name(), "调整 Agent 工具最小调用角色", detail(
            "name", response.name(),
            "label", response.label(),
            "enabled", response.enabled(),
            "minimumRole", response.minimumRole()
        ));
        return ApiResponse.ok(response);
    }

    @GetMapping("/agent-pipeline/nodes")
    public ApiResponse<List<AgentPipelineNodeResponse>> agentPipelineNodes() {
        return ApiResponse.ok(adminService.agentPipelineNodes(currentUserService.getRequiredUser()));
    }

    @PatchMapping("/agent-pipeline/nodes/{name}/enabled")
    public ApiResponse<AgentPipelineNodeResponse> updateAgentPipelineNodeEnabled(@PathVariable String name, @Valid @RequestBody AgentPipelineNodeEnabledRequest request) {
        User admin = currentAdmin();
        AgentPipelineNodeResponse response = adminService.updateAgentPipelineNodeEnabled(
            name,
            request.enabled(),
            admin
        );
        audit(admin, "UPDATE_AGENT_PIPELINE_NODE", "AGENT_PIPELINE_NODE", response.name(), (response.enabled() ? "启用" : "停用") + " Agent Pipeline 节点", detail(
            "pipelineName", response.pipelineName(),
            "type", response.type(),
            "name", response.name(),
            "label", response.label(),
            "enabled", response.enabled()
        ));
        return ApiResponse.ok(response);
    }

    @GetMapping("/ingestion-pipeline/nodes")
    public ApiResponse<List<IngestionPipelineNodeResponse>> ingestionPipelineNodes() {
        return ApiResponse.ok(adminService.ingestionPipelineNodes(currentUserService.getRequiredUser()));
    }

    @GetMapping("/parse-jobs")
    public ApiResponse<PageResponse<ParseJobResponse>> parseJobs(
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(adminService.parseJobs(
            currentUserService.getRequiredUser(),
            status,
            keyword,
            page,
            pageSize
        ));
    }

    @GetMapping("/retrieval-channels")
    public ApiResponse<List<RetrievalChannelCatalogResponse>> retrievalChannels() {
        return ApiResponse.ok(adminService.retrievalChannels(currentUserService.getRequiredUser()));
    }

    @GetMapping("/retrieval-processors")
    public ApiResponse<List<RetrievalProcessorCatalogResponse>> retrievalProcessors() {
        return ApiResponse.ok(adminService.retrievalProcessors(currentUserService.getRequiredUser()));
    }

    @GetMapping("/chunks")
    public ApiResponse<PageResponse<AdminChunkResponse>> chunks(
        @RequestParam(required = false) Long paperId,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(adminService.chunks(
            currentUserService.getRequiredUser(),
            paperId,
            keyword,
            page,
            pageSize
        ));
    }

    @PatchMapping("/chunks/{id}/enabled")
    public ApiResponse<AdminChunkResponse> updateChunkEnabled(@PathVariable Long id, @Valid @RequestBody AdminChunkEnabledRequest request) {
        User admin = currentAdmin();
        AdminChunkResponse response = adminService.updateChunkEnabled(
            id,
            request.enabled(),
            admin
        );
        audit(admin, "UPDATE_CHUNK_ENABLED", "PAPER_CHUNK", response.id(), (response.enabled() ? "启用" : "禁用") + "知识片段检索", detail(
            "paperId", response.paperId(),
            "paperTitle", response.paperTitle(),
            "pageNumber", response.pageNumber(),
            "chunkIndex", response.chunkIndex(),
            "enabled", response.enabled()
        ));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        User admin = currentAdmin();
        AdminUserResponse response = adminService.updateUserStatus(id, request.status(), admin);
        audit(admin, "UPDATE_USER_STATUS", "USER", response.id(), "更新用户账号状态", detail(
            "username", response.username(),
            "email", response.email(),
            "status", response.status(),
            "role", response.role()
        ));
        return ApiResponse.ok(response);
    }

    @GetMapping("/query-term-mappings")
    public ApiResponse<List<QueryTermMappingResponse>> queryTermMappings() {
        return ApiResponse.ok(adminService.queryTermMappings(currentUserService.getRequiredUser()));
    }

    @GetMapping("/rag-settings")
    public ApiResponse<RagSettingsResponse> ragSettings() {
        return ApiResponse.ok(ragSettingsService.get(currentUserService.getRequiredUser()));
    }

    @PatchMapping("/rag-settings")
    public ApiResponse<RagSettingsResponse> updateRagSettings(@Valid @RequestBody RagSettingsRequest request) {
        User admin = currentAdmin();
        RagSettingsResponse response = ragSettingsService.update(request, admin);
        audit(admin, "UPDATE_RAG_SETTINGS", "RAG_SETTINGS", "global", "更新 RAG 运行时设置", detail(
            "candidateLimit", response.candidateLimit(),
            "resultLimit", response.resultLimit(),
            "queryRewriteEnabled", response.queryRewriteEnabled(),
            "answerQualityJudgeEnabled", response.answerQualityJudgeEnabled(),
            "rerankModelEnabled", response.rerankModelEnabled(),
            "chatRateLimitEnabled", response.chatRateLimitEnabled()
        ));
        return ApiResponse.ok(response);
    }

    @GetMapping("/intent-routes")
    public ApiResponse<List<IntentRouteResponse>> intentRoutes() {
        return ApiResponse.ok(intentRouteService.list(currentUserService.getRequiredUser()));
    }

    @PostMapping("/intent-routes")
    public ApiResponse<IntentRouteResponse> createIntentRoute(@Valid @RequestBody IntentRouteRequest request) {
        User admin = currentAdmin();
        IntentRouteResponse response = intentRouteService.create(request, admin);
        audit(admin, "CREATE_INTENT_ROUTE", "INTENT_ROUTE", response.id(), "创建意图路由", intentRouteDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/intent-routes/{id}")
    public ApiResponse<IntentRouteResponse> updateIntentRoute(@PathVariable Long id, @Valid @RequestBody IntentRouteRequest request) {
        User admin = currentAdmin();
        IntentRouteResponse response = intentRouteService.update(id, request, admin);
        audit(admin, "UPDATE_INTENT_ROUTE", "INTENT_ROUTE", response.id(), "更新意图路由", intentRouteDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/intent-routes/{id}")
    public ApiResponse<Void> deleteIntentRoute(@PathVariable Long id) {
        User admin = currentAdmin();
        intentRouteService.delete(id, admin);
        audit(admin, "DELETE_INTENT_ROUTE", "INTENT_ROUTE", id, "删除意图路由", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/answer-prompt-templates")
    public ApiResponse<List<AnswerPromptTemplateResponse>> answerPromptTemplates() {
        return ApiResponse.ok(answerPromptTemplateService.list(currentUserService.getRequiredUser()));
    }

    @PostMapping("/answer-prompt-templates")
    public ApiResponse<AnswerPromptTemplateResponse> createAnswerPromptTemplate(@Valid @RequestBody AnswerPromptTemplateRequest request) {
        User admin = currentAdmin();
        AnswerPromptTemplateResponse response = answerPromptTemplateService.create(request, admin);
        audit(admin, "CREATE_ANSWER_PROMPT_TEMPLATE", "ANSWER_PROMPT_TEMPLATE", response.id(), "创建回答 Prompt 模板", answerPromptTemplateDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/answer-prompt-templates/{id}")
    public ApiResponse<AnswerPromptTemplateResponse> updateAnswerPromptTemplate(@PathVariable Long id, @Valid @RequestBody AnswerPromptTemplateRequest request) {
        User admin = currentAdmin();
        AnswerPromptTemplateResponse response = answerPromptTemplateService.update(id, request, admin);
        audit(admin, "UPDATE_ANSWER_PROMPT_TEMPLATE", "ANSWER_PROMPT_TEMPLATE", response.id(), "更新回答 Prompt 模板", answerPromptTemplateDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/answer-prompt-templates/{id}")
    public ApiResponse<Void> deleteAnswerPromptTemplate(@PathVariable Long id) {
        User admin = currentAdmin();
        answerPromptTemplateService.delete(id, admin);
        audit(admin, "DELETE_ANSWER_PROMPT_TEMPLATE", "ANSWER_PROMPT_TEMPLATE", id, "删除回答 Prompt 模板", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/model-targets")
    public ApiResponse<List<ModelTargetResponse>> modelTargets() {
        return ApiResponse.ok(modelTargetService.list(currentUserService.getRequiredUser()));
    }

    @PostMapping("/model-targets")
    public ApiResponse<ModelTargetResponse> createModelTarget(@Valid @RequestBody ModelTargetRequest request) {
        User admin = currentAdmin();
        ModelTargetResponse response = modelTargetService.create(request, admin);
        audit(admin, "CREATE_MODEL_TARGET", "MODEL_TARGET", response.id(), "创建模型路由目标", modelTargetDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/model-targets/{id}")
    public ApiResponse<ModelTargetResponse> updateModelTarget(@PathVariable Long id, @Valid @RequestBody ModelTargetRequest request) {
        User admin = currentAdmin();
        ModelTargetResponse response = modelTargetService.update(id, request, admin);
        audit(admin, "UPDATE_MODEL_TARGET", "MODEL_TARGET", response.id(), "更新模型路由目标", modelTargetDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/model-targets/{id}")
    public ApiResponse<Void> deleteModelTarget(@PathVariable Long id) {
        User admin = currentAdmin();
        modelTargetService.delete(id, admin);
        audit(admin, "DELETE_MODEL_TARGET", "MODEL_TARGET", id, "删除模型路由目标", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/evaluation-datasets")
    public ApiResponse<List<EvaluationDatasetResponse>> evaluationDatasets() {
        return ApiResponse.ok(agentEvaluationService.datasets(currentUserService.getRequiredUser()));
    }

    @PostMapping("/evaluation-datasets")
    public ApiResponse<EvaluationDatasetResponse> createEvaluationDataset(@Valid @RequestBody EvaluationDatasetRequest request) {
        User admin = currentAdmin();
        EvaluationDatasetResponse response = agentEvaluationService.createDataset(request, admin);
        audit(admin, "CREATE_EVALUATION_DATASET", "EVALUATION_DATASET", response.id(), "创建 Agent 评测集", evaluationDatasetDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/evaluation-datasets/{id}")
    public ApiResponse<EvaluationDatasetResponse> updateEvaluationDataset(@PathVariable Long id, @Valid @RequestBody EvaluationDatasetRequest request) {
        User admin = currentAdmin();
        EvaluationDatasetResponse response = agentEvaluationService.updateDataset(id, request, admin);
        audit(admin, "UPDATE_EVALUATION_DATASET", "EVALUATION_DATASET", response.id(), "更新 Agent 评测集", evaluationDatasetDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/evaluation-datasets/{id}")
    public ApiResponse<Void> deleteEvaluationDataset(@PathVariable Long id) {
        User admin = currentAdmin();
        agentEvaluationService.deleteDataset(id, admin);
        audit(admin, "DELETE_EVALUATION_DATASET", "EVALUATION_DATASET", id, "删除 Agent 评测集", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/evaluation-cases")
    public ApiResponse<PageResponse<EvaluationCaseResponse>> evaluationCases(
        @RequestParam(required = false) Long datasetId,
        @RequestParam(defaultValue = "") String enabled,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(agentEvaluationService.cases(
            currentUserService.getRequiredUser(),
            datasetId,
            enabled,
            keyword,
            page,
            pageSize
        ));
    }

    @PostMapping("/evaluation-cases")
    public ApiResponse<EvaluationCaseResponse> createEvaluationCase(@Valid @RequestBody EvaluationCaseRequest request) {
        User admin = currentAdmin();
        EvaluationCaseResponse response = agentEvaluationService.createCase(request, admin);
        audit(admin, "CREATE_EVALUATION_CASE", "EVALUATION_CASE", response.id(), "创建 Agent 评测样本", evaluationCaseDetail(response));
        return ApiResponse.ok(response);
    }

    @PostMapping("/evaluation-cases/from-trace")
    public ApiResponse<EvaluationCaseResponse> createEvaluationCaseFromTrace(@Valid @RequestBody EvaluationCaseFromTraceRequest request) {
        User admin = currentAdmin();
        EvaluationCaseResponse response = agentEvaluationService.createCaseFromTrace(request, admin);
        audit(admin, "CREATE_EVALUATION_CASE_FROM_TRACE", "EVALUATION_CASE", response.id(), "从 RAG Trace 沉淀 Agent 评测样本", evaluationCaseDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/evaluation-cases/{id}")
    public ApiResponse<EvaluationCaseResponse> updateEvaluationCase(@PathVariable Long id, @Valid @RequestBody EvaluationCaseRequest request) {
        User admin = currentAdmin();
        EvaluationCaseResponse response = agentEvaluationService.updateCase(id, request, admin);
        audit(admin, "UPDATE_EVALUATION_CASE", "EVALUATION_CASE", response.id(), "更新 Agent 评测样本", evaluationCaseDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/evaluation-cases/{id}")
    public ApiResponse<Void> deleteEvaluationCase(@PathVariable Long id) {
        User admin = currentAdmin();
        agentEvaluationService.deleteCase(id, admin);
        audit(admin, "DELETE_EVALUATION_CASE", "EVALUATION_CASE", id, "删除 Agent 评测样本", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/evaluation-runs")
    public ApiResponse<PageResponse<EvaluationRunResponse>> evaluationRuns(
        @RequestParam(required = false) Long datasetId,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(agentEvaluationRunService.runs(
            currentUserService.getRequiredUser(),
            datasetId,
            status,
            page,
            pageSize
        ));
    }

    @PostMapping("/evaluation-runs")
    public ApiResponse<EvaluationRunResponse> startEvaluationRun(@Valid @RequestBody EvaluationRunRequest request) {
        User admin = currentAdmin();
        EvaluationRunResponse response = agentEvaluationRunService.startRun(request, admin);
        audit(admin, "START_EVALUATION_RUN", "EVALUATION_RUN", response.id(), "启动 Agent 评测运行", evaluationRunDetail(response));
        return ApiResponse.ok(response);
    }

    @GetMapping("/evaluation-runs/{id}/results")
    public ApiResponse<PageResponse<EvaluationCaseResultResponse>> evaluationRunResults(
        @PathVariable Long id,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(agentEvaluationRunService.results(
            currentUserService.getRequiredUser(),
            id,
            page,
            pageSize
        ));
    }

    @PostMapping("/query-term-mappings")
    public ApiResponse<QueryTermMappingResponse> createQueryTermMapping(@Valid @RequestBody QueryTermMappingRequest request) {
        User admin = currentAdmin();
        QueryTermMappingResponse response = adminService.createQueryTermMapping(request, admin);
        audit(admin, "CREATE_QUERY_TERM_MAPPING", "QUERY_TERM_MAPPING", response.id(), "创建查询术语映射", queryTermMappingDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/query-term-mappings/{id}")
    public ApiResponse<QueryTermMappingResponse> updateQueryTermMapping(@PathVariable Long id, @Valid @RequestBody QueryTermMappingRequest request) {
        User admin = currentAdmin();
        QueryTermMappingResponse response = adminService.updateQueryTermMapping(id, request, admin);
        audit(admin, "UPDATE_QUERY_TERM_MAPPING", "QUERY_TERM_MAPPING", response.id(), "更新查询术语映射", queryTermMappingDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/query-term-mappings/{id}")
    public ApiResponse<Void> deleteQueryTermMapping(@PathVariable Long id) {
        User admin = currentAdmin();
        adminService.deleteQueryTermMapping(id, admin);
        audit(admin, "DELETE_QUERY_TERM_MAPPING", "QUERY_TERM_MAPPING", id, "删除查询术语映射", detail("id", id));
        return ApiResponse.empty();
    }

    @GetMapping("/sample-prompts")
    public ApiResponse<List<SamplePromptResponse>> samplePrompts() {
        return ApiResponse.ok(samplePromptService.listAll(currentUserService.getRequiredUser()));
    }

    @PostMapping("/sample-prompts")
    public ApiResponse<SamplePromptResponse> createSamplePrompt(@Valid @RequestBody SamplePromptRequest request) {
        User admin = currentAdmin();
        SamplePromptResponse response = samplePromptService.create(request, admin);
        audit(admin, "CREATE_SAMPLE_PROMPT", "SAMPLE_PROMPT", response.id(), "创建示例问题", samplePromptDetail(response));
        return ApiResponse.ok(response);
    }

    @PatchMapping("/sample-prompts/{id}")
    public ApiResponse<SamplePromptResponse> updateSamplePrompt(@PathVariable Long id, @Valid @RequestBody SamplePromptRequest request) {
        User admin = currentAdmin();
        SamplePromptResponse response = samplePromptService.update(id, request, admin);
        audit(admin, "UPDATE_SAMPLE_PROMPT", "SAMPLE_PROMPT", response.id(), "更新示例问题", samplePromptDetail(response));
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/sample-prompts/{id}")
    public ApiResponse<Void> deleteSamplePrompt(@PathVariable Long id) {
        User admin = currentAdmin();
        samplePromptService.delete(id, admin);
        audit(admin, "DELETE_SAMPLE_PROMPT", "SAMPLE_PROMPT", id, "删除示例问题", detail("id", id));
        return ApiResponse.empty();
    }

    private User currentAdmin() {
        return currentUserService.getRequiredUser();
    }

    private void audit(User admin, String action, String resourceType, Object resourceId, String summary, Object detail) {
        adminService.recordAudit(
            admin,
            action,
            resourceType,
            resourceId == null ? null : String.valueOf(resourceId),
            summary,
            detail
        );
    }

    private Map<String, Object> intentRouteDetail(IntentRouteResponse route) {
        return detail(
            "id", route.id(),
            "intentCode", route.intentCode(),
            "label", route.label(),
            "answerStrategy", route.answerStrategy(),
            "boundToolName", route.boundToolName(),
            "comparisonEnabled", route.comparisonEnabled(),
            "enabled", route.enabled()
        );
    }

    private Map<String, Object> answerPromptTemplateDetail(AnswerPromptTemplateResponse template) {
        return detail(
            "id", template.id(),
            "code", template.code(),
            "name", template.name(),
            "enabled", template.enabled(),
            "defaultTemplate", template.defaultTemplate(),
            "sortOrder", template.sortOrder()
        );
    }

    private Map<String, Object> modelTargetDetail(ModelTargetResponse target) {
        return detail(
            "id", target.id(),
            "code", target.code(),
            "provider", target.provider(),
            "taskType", target.taskType(),
            "modelName", target.modelName(),
            "baseUrl", target.baseUrl(),
            "apiKeyConfigured", target.apiKeyConfigured(),
            "enabled", target.enabled(),
            "priority", target.priority(),
            "timeoutSeconds", target.timeoutSeconds()
        );
    }

    private Map<String, Object> queryTermMappingDetail(QueryTermMappingResponse mapping) {
        return detail(
            "id", mapping.id(),
            "term", mapping.term(),
            "enabled", mapping.enabled()
        );
    }

    private Map<String, Object> evaluationDatasetDetail(EvaluationDatasetResponse dataset) {
        return detail(
            "id", dataset.id(),
            "code", dataset.code(),
            "name", dataset.name(),
            "scope", dataset.scope(),
            "enabled", dataset.enabled(),
            "caseCount", dataset.caseCount()
        );
    }

    private Map<String, Object> evaluationCaseDetail(EvaluationCaseResponse item) {
        return detail(
            "id", item.id(),
            "datasetId", item.datasetId(),
            "datasetCode", item.datasetCode(),
            "paperId", item.paperId(),
            "chatRecordId", item.chatRecordId(),
            "ragTraceId", item.ragTraceId(),
            "scope", item.scope(),
            "difficulty", item.difficulty(),
            "tags", item.tags(),
            "enabled", item.enabled()
        );
    }

    private Map<String, Object> evaluationRunDetail(EvaluationRunResponse run) {
        return detail(
            "id", run.id(),
            "datasetId", run.datasetId(),
            "datasetCode", run.datasetCode(),
            "runName", run.runName(),
            "status", run.status(),
            "caseCount", run.caseCount(),
            "completedCount", run.completedCount(),
            "averageScore", run.averageScore()
        );
    }

    private Map<String, Object> samplePromptDetail(SamplePromptResponse prompt) {
        return detail(
            "id", prompt.id(),
            "scope", prompt.scope(),
            "title", prompt.title(),
            "sortOrder", prompt.sortOrder(),
            "enabled", prompt.enabled()
        );
    }

    private Map<String, Object> detail(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }
}
