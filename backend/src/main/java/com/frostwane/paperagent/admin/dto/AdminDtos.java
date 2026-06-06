package com.frostwane.paperagent.admin.dto;

import com.frostwane.paperagent.user.UserStatus;
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
        long totalSamplePrompts,
        long enabledSamplePrompts,
        int averageLatencyMs,
        long failedTraces,
        int averageRetrievalMs,
        int averageGenerationMs,
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
        String scope,
        String question,
        String status,
        String modelName,
        String pipelineName,
        String queryIntent,
        String searchQuery,
        List<QueryExpansionResponse> queryExpansions,
        boolean comparisonRequested,
        String answerStrategy,
        String answerContract,
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
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
}
