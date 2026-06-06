package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream()
            .sorted(Comparator.comparing(AgentTool::name))
            .toList();
    }

    public List<AgentTool> matchingTools(AgentPipelineContext context) {
        return tools.stream()
            .filter(tool -> tool.supports(context))
            .toList();
    }
}
