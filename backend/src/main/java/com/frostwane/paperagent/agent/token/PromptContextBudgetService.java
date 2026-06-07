package com.frostwane.paperagent.agent.token;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PromptContextBudgetService {

    private static final int SOURCE_OVERHEAD_TOKENS = 18;

    private final TokenCounterService tokenCounterService;

    public PromptContextBudgetService(TokenCounterService tokenCounterService) {
        this.tokenCounterService = tokenCounterService;
    }

    public PromptContextBudgetResult apply(List<SourceResponse> sources, int tokenBudget) {
        int safeBudget = Math.max(0, tokenBudget);
        if (sources == null || sources.isEmpty() || safeBudget == 0) {
            return new PromptContextBudgetResult(List.of(), safeBudget, 0, sources != null && !sources.isEmpty());
        }

        List<SourceResponse> retained = new ArrayList<>();
        int usedTokens = 0;
        boolean truncated = false;
        for (SourceResponse source : sources) {
            int metadataTokens = metadataTokens(source);
            int remaining = safeBudget - usedTokens - metadataTokens;
            if (remaining <= 0) {
                truncated = true;
                break;
            }

            String content = source.content() == null ? "" : source.content();
            int contentTokens = tokenCounterService.estimateTokens(content);
            if (contentTokens <= remaining) {
                retained.add(source);
                usedTokens += metadataTokens + contentTokens;
                continue;
            }

            int ellipsisTokens = tokenCounterService.estimateTokens("...");
            String compacted = tokenCounterService.truncateToTokenBudget(content, Math.max(0, remaining - ellipsisTokens));
            if (!compacted.isBlank()) {
                String clipped = compacted + "...";
                retained.add(new SourceResponse(
                    source.paperId(),
                    source.title(),
                    source.page(),
                    clipped
                ));
                usedTokens += metadataTokens + tokenCounterService.estimateTokens(clipped);
            }
            truncated = true;
            break;
        }
        return new PromptContextBudgetResult(List.copyOf(retained), safeBudget, usedTokens, truncated || retained.size() < sources.size());
    }

    private int metadataTokens(SourceResponse source) {
        String title = source == null ? "" : source.title();
        return SOURCE_OVERHEAD_TOKENS + tokenCounterService.estimateTokens(title);
    }
}
