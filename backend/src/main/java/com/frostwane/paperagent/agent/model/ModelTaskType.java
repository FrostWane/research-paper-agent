package com.frostwane.paperagent.agent.model;

import java.util.Locale;

public enum ModelTaskType {
    GENERAL,
    ANSWER_GENERATION,
    QUERY_REWRITE,
    QUALITY_EVALUATION,
    CONVERSATION_SUMMARY;

    public String code() {
        return name();
    }

    public static ModelTaskType fromCode(String value, ModelTaskType fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? GENERAL : fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        for (ModelTaskType taskType : values()) {
            if (taskType.name().equals(normalized)) {
                return taskType;
            }
        }
        throw new IllegalArgumentException("Unsupported model task type: " + value);
    }
}
