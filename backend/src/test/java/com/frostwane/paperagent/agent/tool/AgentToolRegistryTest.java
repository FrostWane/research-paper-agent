package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    @Test
    void matchingToolsSkipsDisabledTools() {
        AgentToolSettingService settings = mock(AgentToolSettingService.class);
        when(settings.enabled("enabled-tool")).thenReturn(true);
        when(settings.enabled("disabled-tool")).thenReturn(false);

        AgentToolRegistry registry = new AgentToolRegistry(List.of(
            new StubTool("disabled-tool"),
            new StubTool("enabled-tool")
        ), settings);

        assertThat(registry.matchingTools(null))
            .extracting(AgentTool::name)
            .containsExactly("enabled-tool");
    }

    private record StubTool(String name) implements AgentTool {
        @Override
        public String label() {
            return name;
        }

        @Override
        public String description() {
            return name;
        }

        @Override
        public boolean supports(AgentPipelineContext context) {
            return true;
        }

        @Override
        public ToolExecutionOutput execute(AgentPipelineContext context) {
            return new ToolExecutionOutput(name, name);
        }
    }
}
