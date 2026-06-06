package com.frostwane.paperagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record ChatRequest(
        Long paperId,
        @NotBlank @Size(max = 4000) String question,
        boolean useRag
    ) {
    }

    public record SourceResponse(
        Long paperId,
        String title,
        int page,
        String content
    ) {
    }

    public record ChatResponse(
        String answer,
        List<SourceResponse> sources,
        Long recordId,
        String modelName,
        int latencyMs
    ) {
    }

    public record ChatRecordResponse(
        Long id,
        Long paperId,
        String question,
        String answer,
        List<SourceResponse> sources,
        String modelName,
        Integer latencyMs,
        Integer feedbackScore,
        String feedbackComment,
        OffsetDateTime feedbackAt,
        OffsetDateTime createdAt
    ) {
    }

    public record ChatFeedbackRequest(
        @Min(-1) @Max(1) Integer score,
        @Size(max = 500) String comment
    ) {
    }

    public record SamplePromptResponse(
        Long id,
        String scope,
        String title,
        String prompt,
        String description,
        Integer sortOrder,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record SamplePromptRequest(
        @NotBlank @Size(max = 32) String scope,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String prompt,
        @Size(max = 255) String description,
        @Min(0) @Max(10000) Integer sortOrder,
        Boolean enabled
    ) {
    }
}
