package com.frostwane.paperagent.agent.token;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;

import java.util.List;

public record PromptContextBudgetResult(
    List<SourceResponse> sources,
    int tokenBudget,
    int estimatedTokens,
    boolean truncated
) {
}
