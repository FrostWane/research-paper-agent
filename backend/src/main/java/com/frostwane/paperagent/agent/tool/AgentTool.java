package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;

public interface AgentTool {

    String name();

    String label();

    String description();

    boolean supports(AgentPipelineContext context);

    ToolExecutionOutput execute(AgentPipelineContext context);
}
