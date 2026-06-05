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
        List<StatusCountResponse> processStatuses,
        List<ModelUsageResponse> modelUsage,
        List<RecentPaperResponse> recentPapers
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
