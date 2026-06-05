package com.frostwane.paperagent.agent.retrieval;

public record RetrievalChannelTrace(
    String name,
    String label,
    String status,
    int candidateCount,
    int latencyMs,
    String errorMessage
) {
    public static RetrievalChannelTrace from(RetrievalChannelResult result) {
        return new RetrievalChannelTrace(
            result.name(),
            result.label(),
            result.status(),
            result.candidates().size(),
            result.latencyMs(),
            result.errorMessage()
        );
    }
}
