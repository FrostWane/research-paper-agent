package com.frostwane.paperagent.paper.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class PaperDtos {
    private PaperDtos() {
    }

    public record PaperRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 4000) String authors,
        @Size(max = 255) String venue,
        @Min(1900) @Max(2200) Integer year,
        @Size(max = 512) String keywords,
        @Size(max = 20000) String abstractText,
        @Size(max = 20000) String note,
        Long fileId
    ) {
    }

    public record StatusRequest(
        @NotBlank String status
    ) {
    }

    public record PaperResponse(
        Long id,
        String title,
        String authors,
        String venue,
        Integer year,
        String keywords,
        String abstractText,
        String note,
        String status,
        String processStatus,
        Long fileId,
        String fileName,
        Long fileSize,
        Integer pageCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record ParseStatusResponse(
        Long paperId,
        String status,
        String message,
        int progress,
        long chunkCount
    ) {
    }
}
