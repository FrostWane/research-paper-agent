package com.frostwane.paperagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
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
        OffsetDateTime createdAt
    ) {
    }
}
