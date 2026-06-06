package com.frostwane.paperagent.agent.pipeline;

public enum AgentNodeType {
    SCOPE_RESOLUTION,
    MEMORY,
    QUERY_REWRITE,
    QUERY_PLANNING,
    TOOL_EXECUTION,
    RETRIEVAL,
    ANSWER_PLANNING,
    GENERATION,
    VERIFICATION,
    EVALUATION,
    FORMATTING
}
