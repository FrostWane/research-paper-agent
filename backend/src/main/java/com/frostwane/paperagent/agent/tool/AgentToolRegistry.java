package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;
    private final AgentToolSettingService settingService;

    public AgentToolRegistry(List<AgentTool> tools, AgentToolSettingService settingService) {
        this.tools = tools.stream()
            .sorted(Comparator.comparing(AgentTool::name))
            .toList();
        this.settingService = settingService;
    }

    public List<AgentTool> matchingTools(AgentPipelineContext context) {
        UserRole role = context == null || context.owner() == null ? UserRole.USER : context.owner().getRole();
        return tools.stream()
            .filter(tool -> settingService.available(tool.name(), role))
            .filter(tool -> requested(context, tool) || tool.supports(context))
            .toList();
    }

    public List<AgentTool> tools() {
        return tools;
    }

    private boolean requested(AgentPipelineContext context, AgentTool tool) {
        return context != null && context.requestedToolNames().stream()
            .anyMatch(name -> tool.name().equalsIgnoreCase(name));
    }
}
