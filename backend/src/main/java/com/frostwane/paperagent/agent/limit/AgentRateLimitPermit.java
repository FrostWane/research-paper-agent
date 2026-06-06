package com.frostwane.paperagent.agent.limit;

public final class AgentRateLimitPermit implements AutoCloseable {

    private static final AgentRateLimitPermit NOOP = new AgentRateLimitPermit(null, null);

    private final AgentRateLimiterService limiterService;
    private final Long userId;
    private boolean closed;

    private AgentRateLimitPermit(AgentRateLimiterService limiterService, Long userId) {
        this.limiterService = limiterService;
        this.userId = userId;
    }

    static AgentRateLimitPermit acquired(AgentRateLimiterService limiterService, Long userId) {
        return new AgentRateLimitPermit(limiterService, userId);
    }

    static AgentRateLimitPermit noop() {
        return NOOP;
    }

    @Override
    public void close() {
        if (limiterService == null || closed) {
            return;
        }
        closed = true;
        limiterService.release(userId);
    }
}
