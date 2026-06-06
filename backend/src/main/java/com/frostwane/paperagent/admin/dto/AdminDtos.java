package com.frostwane.paperagent.admin.dto;

import com.frostwane.paperagent.user.UserStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record AdminOverviewResponse(
        long totalUsers,
        long normalUsers,
        long disabledUsers,
        long totalPapers,
        long indexedPapers,
        long failedPapers,
        long totalFiles,
        long storageBytes,
        long totalChunks,
        long embeddedChunks,
        long totalChats,
        long libraryChats,
        long totalFeedbacks,
        long positiveFeedbacks,
        long negativeFeedbacks,
        long totalQueryMappings,
        long enabledQueryMappings,
        long totalIntentRoutes,
        long enabledIntentRoutes,
        long totalAnswerPromptTemplates,
        long enabledAnswerPromptTemplates,
        long totalModelTargets,
        long enabledModelTargets,
        long totalSamplePrompts,
        long enabledSamplePrompts,
        int averageLatencyMs,
        long failedTraces,
        int averageRetrievalMs,
        int averageGenerationMs,
        int averageAnswerQualityScore,
        long totalParseJobs,
        long failedParseJobs,
        int averageParseMs,
        List<StatusCountResponse> processStatuses,
        List<ModelUsageResponse> modelUsage,
        List<ModelHealthResponse> modelHealth,
        List<RecentPaperResponse> recentPapers,
        List<ParseJobResponse> recentParseJobs,
        List<RagTraceResponse> recentTraces
    ) {
    }

    public record StatusCountResponse(
        String status,
        long count
    ) {
    }

    public record ModelUsageResponse(
        String modelName,
        long count,
        int averageLatencyMs
    ) {
    }

    public record ModelHealthResponse(
        String taskType,
        String provider,
        String modelName,
        String targetName,
        String lastStatus,
        long totalCalls,
        long successCalls,
        long failedCalls,
        long fallbackCalls,
        int averageLatencyMs,
        OffsetDateTime lastSeenAt
    ) {
    }

    public record RecentPaperResponse(
        Long id,
        String title,
        String owner,
        String processStatus,
        OffsetDateTime updatedAt
    ) {
    }

    public record RagTraceResponse(
        Long id,
        String username,
        Long paperId,
        String paperTitle,
        Long sessionId,
        String sessionTitle,
        String scope,
        String question,
        String status,
        String modelName,
        String pipelineName,
        String queryIntent,
        String searchQuery,
        String rewrittenQuery,
        List<String> querySubQuestions,
        boolean queryRewriteEnabled,
        String queryRewriteModelName,
        List<QueryExpansionResponse> queryExpansions,
        boolean comparisonRequested,
        String answerStrategy,
        String answerContract,
        List<ToolExecutionResponse> toolExecutions,
        int sourceCount,
        int memoryTurnCount,
        int memoryChars,
        boolean memorySummaryUsed,
        int memorySummaryTurnCount,
        int memorySummaryChars,
        String memorySummaryMethod,
        String memorySummaryModelName,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int evaluationMs,
        int answerQualityScore,
        String answerQualityLabel,
        String answerQualityNotes,
        String answerQualityMethod,
        boolean answerQualityJudgeEnabled,
        String answerQualityJudgeModelName,
        int answerQualityConfidence,
        int totalMs,
        String errorMessage,
        List<RagTraceRetrievalChannelResponse> retrievalChannels,
        List<RagTraceRetrievalProcessorResponse> retrievalProcessors,
        List<RagTraceNodeSpanResponse> nodeSpans,
        OffsetDateTime createdAt
    ) {
    }

    public record RagTraceRetrievalChannelResponse(
        String name,
        String label,
        String status,
        int candidateCount,
        int latencyMs,
        String errorMessage
    ) {
    }

    public record QueryExpansionResponse(
        Long id,
        String term,
        List<String> expansions
    ) {
    }

    public record ToolExecutionResponse(
        String name,
        String label,
        String status,
        String summary,
        String details,
        int latencyMs,
        String errorMessage
    ) {
    }

    public record RagTraceRetrievalProcessorResponse(
        String name,
        String label,
        String status,
        int inputCount,
        int outputCount,
        int latencyMs,
        String errorMessage
    ) {
    }

    public record RagTraceNodeSpanResponse(
        String type,
        String name,
        int order,
        String status,
        int durationMs,
        String errorMessage
    ) {
    }

    public record ParseJobResponse(
        Long id,
        String username,
        Long paperId,
        String paperTitle,
        String fileName,
        long fileSize,
        String status,
        int pageCount,
        int chunkCount,
        int durationMs,
        String errorMessage,
        List<ParseJobNodeSpanResponse> nodeSpans,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
    ) {
    }

    public record ParseJobNodeSpanResponse(
        String type,
        String name,
        String label,
        int order,
        String status,
        int durationMs,
        String errorMessage
    ) {
    }

    public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        long paperCount,
        long indexedPaperCount,
        long chatCount,
        long fileCount,
        long storageBytes,
        int averageLatencyMs,
        OffsetDateTime createdAt
    ) {
    }

    public record UserStatusUpdateRequest(
        @NotNull UserStatus status
    ) {
    }

    public record QueryTermMappingResponse(
        Long id,
        String term,
        String expansions,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record QueryTermMappingRequest(
        @NotBlank @Size(max = 120) String term,
        @NotBlank @Size(max = 1000) String expansions,
        Boolean enabled
    ) {
    }

    public record IntentRouteResponse(
        Long id,
        String intentCode,
        String label,
        String description,
        String keywords,
        String searchHint,
        String answerStrategy,
        String answerContract,
        boolean comparisonEnabled,
        boolean enabled,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record IntentRouteRequest(
        @NotBlank @Size(max = 64) String intentCode,
        @NotBlank @Size(max = 120) String label,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 2000) String keywords,
        @Size(max = 500) String searchHint,
        @NotBlank @Size(max = 64) String answerStrategy,
        @Size(max = 2000) String answerContract,
        Boolean comparisonEnabled,
        Boolean enabled,
        @Min(0) @Max(1000) Integer sortOrder
    ) {
    }

    public record AnswerPromptTemplateResponse(
        Long id,
        String code,
        String name,
        String description,
        String systemPrompt,
        String userPromptTemplate,
        boolean enabled,
        boolean defaultTemplate,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record AnswerPromptTemplateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 8000) String systemPrompt,
        @NotBlank @Size(max = 12000) String userPromptTemplate,
        Boolean enabled,
        Boolean defaultTemplate,
        @Min(0) @Max(1000) Integer sortOrder
    ) {
    }

    public record ModelTargetResponse(
        Long id,
        String code,
        String provider,
        String taskType,
        String modelName,
        String description,
        String baseUrl,
        boolean apiKeyConfigured,
        boolean enabled,
        int priority,
        int timeoutSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record ModelTargetRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 64) String provider,
        @Size(max = 64) String taskType,
        @NotBlank @Size(max = 160) String modelName,
        @Size(max = 500) String description,
        @Size(max = 500) String baseUrl,
        @Size(max = 2000) String apiKey,
        Boolean enabled,
        @Min(0) @Max(1000) Integer priority,
        @Min(1) @Max(120) Integer timeoutSeconds
    ) {
    }

    public record RagSettingsResponse(
        int candidateLimit,
        int resultLimit,
        int sourceExcerptChars,
        double vectorWeight,
        double keywordWeight,
        int memoryHistoryTurns,
        int memoryMaxChars,
        boolean memorySummaryEnabled,
        int memorySummaryStartTurns,
        int memorySummaryMaxChars,
        boolean queryRewriteEnabled,
        int queryRewriteMaxSubQuestions,
        boolean answerQualityJudgeEnabled,
        boolean rerankModelEnabled,
        int rerankModelMaxCandidates,
        OffsetDateTime updatedAt
    ) {
    }

    public record RagSettingsRequest(
        @Min(1) @Max(50) Integer candidateLimit,
        @Min(1) @Max(20) Integer resultLimit,
        @Min(120) @Max(1200) Integer sourceExcerptChars,
        @DecimalMin("0.0") @DecimalMax("3.0") Double vectorWeight,
        @DecimalMin("0.0") @DecimalMax("3.0") Double keywordWeight,
        @Min(0) @Max(12) Integer memoryHistoryTurns,
        @Min(0) @Max(8000) Integer memoryMaxChars,
        Boolean memorySummaryEnabled,
        @Min(2) @Max(50) Integer memorySummaryStartTurns,
        @Min(300) @Max(6000) Integer memorySummaryMaxChars,
        Boolean queryRewriteEnabled,
        @Min(1) @Max(6) Integer queryRewriteMaxSubQuestions,
        Boolean answerQualityJudgeEnabled,
        Boolean rerankModelEnabled,
        @Min(2) @Max(20) Integer rerankModelMaxCandidates
    ) {
    }
}
