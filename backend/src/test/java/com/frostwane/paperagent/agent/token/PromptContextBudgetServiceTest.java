package com.frostwane.paperagent.agent.token;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptContextBudgetServiceTest {

    private final TokenCounterService tokenCounterService = new HeuristicTokenCounterService();
    private final PromptContextBudgetService service = new PromptContextBudgetService(tokenCounterService);

    @Test
    void keepsSourcesWhenTheyFitBudget() {
        PromptContextBudgetResult result = service.apply(List.of(
            new SourceResponse(1L, "Paper A", 1, "short evidence"),
            new SourceResponse(2L, "Paper B", 2, "另一段证据")
        ), 200);

        assertThat(result.sources()).hasSize(2);
        assertThat(result.truncated()).isFalse();
        assertThat(result.estimatedTokens()).isGreaterThan(0).isLessThanOrEqualTo(200);
    }

    @Test
    void truncatesSourceContentWhenBudgetIsTight() {
        SourceResponse source = new SourceResponse(
            1L,
            "Long Paper",
            3,
            "这是一段非常长的中文证据材料，用来模拟 PDF 正文片段在提示词预算不足时被裁剪的情况。"
        );

        PromptContextBudgetResult result = service.apply(List.of(source), 35);

        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().content()).endsWith("...");
        assertThat(result.sources().getFirst().content().length()).isLessThan(source.content().length());
        assertThat(result.truncated()).isTrue();
        assertThat(result.estimatedTokens()).isLessThanOrEqualTo(35);
    }

    @Test
    void dropsRemainingSourcesAfterBudgetIsUsed() {
        SourceResponse first = new SourceResponse(1L, "First", 1, "第一段内容较长，需要占据大部分上下文预算。");
        SourceResponse second = new SourceResponse(2L, "Second", 2, "第二段不应该进入预算。");

        PromptContextBudgetResult result = service.apply(List.of(first, second), 38);

        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().title()).isEqualTo("First");
        assertThat(result.truncated()).isTrue();
    }
}
