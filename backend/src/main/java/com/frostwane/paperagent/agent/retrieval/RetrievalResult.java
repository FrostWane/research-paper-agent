package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;

import java.util.List;

public record RetrievalResult(
    List<SourceResponse> sources,
    List<RetrievalChannelTrace> channels
) {
    public static RetrievalResult empty() {
        return new RetrievalResult(List.of(), List.of());
    }
}
