package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    @Test
    void matchingToolsSkipsDisabledTools() {
        AgentToolSettingService settings = mock(AgentToolSettingService.class);
        when(settings.available("enabled-tool", UserRole.USER)).thenReturn(true);
        when(settings.available("disabled-tool", UserRole.USER)).thenReturn(false);

        AgentToolRegistry registry = new AgentToolRegistry(List.of(
            new StubTool("disabled-tool"),
            new StubTool("enabled-tool")
        ), settings);

        assertThat(registry.matchingTools(null))
            .extracting(AgentTool::name)
            .containsExactly("enabled-tool");
    }

    @Test
    void matchingToolsRespectsMinimumRole() {
        AgentToolSettingService settings = mock(AgentToolSettingService.class);
        when(settings.available("admin-tool", UserRole.USER)).thenReturn(false);
        when(settings.available("user-tool", UserRole.USER)).thenReturn(true);
        when(settings.available("admin-tool", UserRole.ADMIN)).thenReturn(true);
        when(settings.available("user-tool", UserRole.ADMIN)).thenReturn(true);

        AgentToolRegistry registry = new AgentToolRegistry(List.of(
            new StubTool("admin-tool"),
            new StubTool("user-tool")
        ), settings);

        assertThat(registry.matchingTools(context(UserRole.USER)))
            .extracting(AgentTool::name)
            .containsExactly("user-tool");
        assertThat(registry.matchingTools(context(UserRole.ADMIN)))
            .extracting(AgentTool::name)
            .containsExactly("admin-tool", "user-tool");
    }

    private AgentPipelineContext context(UserRole role) {
        User user = new User();
        user.setRole(role);
        return new AgentPipelineContext(new ChatRequest(null, null, "统计一下文献库", true), user);
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
