package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentGuidanceNodeTest {

    private final IntentGuidanceNode node = new IntentGuidanceNode();

    @Test
    void detectsVagueSummaryQuestion() {
        AgentPipelineContext context = context(null, "总结一下", true);

        node.execute(context);

        assertThat(context.guidanceRequired()).isTrue();
        assertThat(context.guidanceType()).isEqualTo("AMBIGUOUS_QUESTION");
        assertThat(context.guidanceSuggestions()).hasSize(3);
    }

    @Test
    void keepsSpecificQuestionWithSourcesUnguided() {
        AgentPipelineContext context = context(1L, "请分析注意力机制在实验设置中的作用", true);
        context.sources(List.of(new SourceResponse(1L, "Test Paper", 3, "attention ablation study")));

        node.execute(context);

        assertThat(context.guidanceRequired()).isFalse();
        assertThat(context.guidanceType()).isEqualTo("NONE");
    }

    @Test
    void guidesWhenRagHasNoEvidence() {
        AgentPipelineContext context = context(1L, "请分析注意力机制在实验设置中的作用", true);

        node.execute(context);

        assertThat(context.guidanceRequired()).isTrue();
        assertThat(context.guidanceType()).isEqualTo("PAPER_EVIDENCE_GAP");
        assertThat(context.guidanceContext()).contains("当前论文检索没有命中可引用片段");
    }

    private AgentPipelineContext context(Long paperId, String question, boolean useRag) {
        return new AgentPipelineContext(new ChatRequest(null, paperId, question, useRag), new User());
    }
}
