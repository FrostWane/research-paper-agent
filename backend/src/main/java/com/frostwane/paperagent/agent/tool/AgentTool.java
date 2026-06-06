package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;

public interface AgentTool {

    String name();

    String label();

    String description();

    default String triggerDescription() {
        return "由工具自身根据问题、范围和上下文自动匹配。";
    }

    boolean supports(AgentPipelineContext context);

    ToolExecutionOutput execute(AgentPipelineContext context);
}
