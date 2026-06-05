package com.frostwane.paperagent.agent.retrieval;

import java.util.List;

public record RetrievalChannelResult(
    String name,
    String label,
    String status,
    List<RetrievalCandidate> candidates,
    int latencyMs,
    String errorMessage
) {
    public static RetrievalChannelResult success(RetrievalChannel channel, List<RetrievalCandidate> candidates, int latencyMs) {
        return new RetrievalChannelResult(channel.name(), channel.label(), "SUCCESS", candidates, latencyMs, null);
    }

    public static RetrievalChannelResult failure(RetrievalChannel channel, int latencyMs, Exception exception) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return new RetrievalChannelResult(channel.name(), channel.label(), "FAILED", List.of(), latencyMs, sanitize(message));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 400 ? sanitized.substring(0, 400) : sanitized;
    }
}
