package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPipelineTest {

    @Test
    void executeSkipsDisabledOptionalNodeAndRecordsSpan() {
        AgentPipelineNodeSettingService settings = mock(AgentPipelineNodeSettingService.class);
        AgentNode disabled = new StubNode(AgentNodeType.QUERY_REWRITE, "query-rewrite-and-split", 14);
        AgentNode enabled = new StubNode(AgentNodeType.QUERY_PLANNING, "query-planning", 16);
        when(settings.enabled(disabled)).thenReturn(false);
        when(settings.enabled(enabled)).thenReturn(true);

        AgentPipeline pipeline = new AgentPipeline(List.of(disabled, enabled), settings);
        AgentPipelineContext context = new AgentPipelineContext(new ChatRequest(null, null, "请总结一下", true), new User());

        pipeline.execute(context);

        assertThat(context.nodeSpans()).extracting(AgentPipelineContext.NodeSpan::status)
            .containsExactly("SKIPPED", "SUCCESS");
        assertThat(((StubNode) disabled).runs()).isZero();
        assertThat(((StubNode) enabled).runs()).isEqualTo(1);
    }

    private static class StubNode implements AgentNode {
        private final AgentNodeType type;
        private final String name;
        private final int order;
        private int runs;

        private StubNode(AgentNodeType type, String name, int order) {
            this.type = type;
            this.name = name;
            this.order = order;
        }

        @Override
        public AgentNodeType type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public void execute(AgentPipelineContext context) {
            runs++;
        }

        private int runs() {
            return runs;
        }
    }
}
