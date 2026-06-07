package com.frostwane.paperagent.agent.token;

public interface TokenCounterService {
    int estimateTokens(String text);

    String truncateToTokenBudget(String text, int tokenBudget);
}
