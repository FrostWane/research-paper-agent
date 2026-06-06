package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.tool.AgentTool;
import com.frostwane.paperagent.agent.tool.AgentToolRegistry;
import com.frostwane.paperagent.agent.tool.ToolExecutionOutput;
import com.frostwane.paperagent.agent.tool.ToolExecutionTrace;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ToolExecutionNode implements AgentNode {

    private final AgentToolRegistry toolRegistry;

    public ToolExecutionNode(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.TOOL_EXECUTION;
    }

    @Override
    public String name() {
        return "tool-execution";
    }

    @Override
    public int order() {
        return 18;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        List<ToolExecutionTrace> traces = new ArrayList<>();
        for (AgentTool tool : toolRegistry.matchingTools(context)) {
            Instant started = Instant.now();
            try {
                ToolExecutionOutput output = tool.execute(context);
                traces.add(ToolExecutionTrace.success(tool, output, elapsedMs(started)));
            } catch (RuntimeException ex) {
                traces.add(ToolExecutionTrace.failure(tool, elapsedMs(started), ex));
            }
        }
        context.toolExecutions(traces);
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }
}
