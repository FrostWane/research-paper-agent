package com.frostwane.paperagent.agent.limit;

public record AgentRateLimitStatus(
    boolean enabled,
    int activeGlobal,
    int activeUsers,
    int recentRequests,
    int globalConcurrencyLimit,
    int userConcurrencyLimit,
    int userPerMinuteLimit
) {
}
