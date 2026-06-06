package com.frostwane.paperagent.agent.settings;

public record RagSettingsSnapshot(
    int candidateLimit,
    int resultLimit,
    int sourceExcerptChars,
    double vectorWeight,
    double keywordWeight
) {
}
