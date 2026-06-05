package com.frostwane.paperagent.admin.dto;

import com.frostwane.paperagent.user.UserStatus;
import jakarta.validation.constraints.NotNull;

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
        int averageLatencyMs,
        long failedTraces,
        int averageRetrievalMs,
        int averageGenerationMs,
        long totalParseJobs,
        long failedParseJobs,
        int averageParseMs,
        List<StatusCountResponse> processStatuses,
        List<ModelUsageResponse> modelUsage,
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
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int totalMs,
        String errorMessage,
        List<RagTraceNodeSpanResponse> nodeSpans,
        OffsetDateTime createdAt
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
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
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
}
