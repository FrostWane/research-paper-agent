package com.frostwane.paperagent.agent.pipeline;

public interface AgentNode {

    AgentNodeType type();

    String name();

    int order();

    void execute(AgentPipelineContext context);
}
