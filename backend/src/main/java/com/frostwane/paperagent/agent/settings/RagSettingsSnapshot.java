package com.frostwane.paperagent.agent.settings;

public record RagSettingsSnapshot(
    int candidateLimit,
    int resultLimit,
    int sourceExcerptChars,
    int contextTokenBudget,
    double vectorWeight,
    double keywordWeight,
    int memoryHistoryTurns,
    int memoryMaxChars,
    boolean memorySummaryEnabled,
    int memorySummaryStartTurns,
    int memorySummaryMaxChars,
    boolean queryRewriteEnabled,
    int queryRewriteMaxSubQuestions,
    boolean answerQualityJudgeEnabled,
    boolean rerankModelEnabled,
    int rerankModelMaxCandidates,
    boolean chatRateLimitEnabled,
    int chatRateLimitGlobalConcurrency,
    int chatRateLimitUserConcurrency,
    int chatRateLimitUserPerMinute
) {
}
