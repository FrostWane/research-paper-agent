package com.frostwane.paperagent.agent.tool;

public record ToolExecutionTrace(
    String name,
    String label,
    String status,
    String summary,
    String details,
    int latencyMs,
    String errorMessage
) {
    public static ToolExecutionTrace success(AgentTool tool, ToolExecutionOutput output, int latencyMs) {
        return new ToolExecutionTrace(
            tool.name(),
            tool.label(),
            "SUCCESS",
            output == null ? "" : safe(output.summary()),
            output == null ? "" : safe(output.details()),
            Math.max(0, latencyMs),
            null
        );
    }

    public static ToolExecutionTrace failure(AgentTool tool, int latencyMs, RuntimeException ex) {
        return new ToolExecutionTrace(
            tool.name(),
            tool.label(),
            "FAILED",
            "",
            "",
            Math.max(0, latencyMs),
            sanitize(ex)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sanitize(RuntimeException ex) {
        String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        String sanitized = message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
