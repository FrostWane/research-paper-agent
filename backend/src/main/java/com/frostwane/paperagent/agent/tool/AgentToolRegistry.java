package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
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
        return tools.stream()
            .filter(tool -> settingService.enabled(tool.name()))
            .filter(tool -> tool.supports(context))
            .toList();
    }

    public List<AgentTool> tools() {
        return tools;
    }
}
