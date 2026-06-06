package com.frostwane.paperagent.agent.retrieval;

public record RetrievalProcessorTrace(
    String name,
    String label,
    String status,
    int inputCount,
    int outputCount,
    int latencyMs,
    String errorMessage
) {
    public static RetrievalProcessorTrace success(
        RetrievalPostProcessor processor,
        int inputCount,
        int outputCount,
        int latencyMs
    ) {
        return new RetrievalProcessorTrace(processor.name(), processor.label(), "SUCCESS", inputCount, outputCount, latencyMs, null);
    }

    public static RetrievalProcessorTrace failure(
        RetrievalPostProcessor processor,
        int inputCount,
        int outputCount,
        int latencyMs,
        Exception exception
    ) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return new RetrievalProcessorTrace(
            processor.name(),
            processor.label(),
            "FAILED",
            inputCount,
            outputCount,
            latencyMs,
            sanitize(message)
        );
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 400 ? sanitized.substring(0, 400) : sanitized;
    }
}
