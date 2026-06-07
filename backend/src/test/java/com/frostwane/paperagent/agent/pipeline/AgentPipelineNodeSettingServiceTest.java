package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPipelineNodeSettingServiceTest {

    @Test
    void updateEnabledRejectsDisablingCoreNode() {
        AgentPipelineNodeSettingService service = new AgentPipelineNodeSettingService(mock(AgentPipelineNodeSettingRepository.class));

        assertThatThrownBy(() -> service.updateEnabled(new StubNode(AgentNodeType.GENERATION, "answer-generation"), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("主链路");
    }

    @Test
    void updateEnabledPersistsOptionalNodeState() {
        AgentPipelineNodeSettingRepository repository = mock(AgentPipelineNodeSettingRepository.class);
        when(repository.findById("intent-guidance")).thenReturn(Optional.empty());
        when(repository.save(any(AgentPipelineNodeSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentPipelineNodeSettingService service = new AgentPipelineNodeSettingService(repository);

        boolean enabled = service.updateEnabled(new StubNode(AgentNodeType.INTENT_GUIDANCE, "intent-guidance"), false);

        assertThat(enabled).isFalse();
    }

    private record StubNode(AgentNodeType type, String name) implements AgentNode {
        @Override
        public int order() {
            return 0;
        }

        @Override
        public void execute(AgentPipelineContext context) {
        }
    }
}
