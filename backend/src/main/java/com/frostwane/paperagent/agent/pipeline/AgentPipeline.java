package com.frostwane.paperagent.agent.pipeline;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class AgentPipeline {

    public static final String NAME = "agent-pipeline-v1";

    private final List<AgentNode> nodes;

    public AgentPipeline(List<AgentNode> nodes) {
        this.nodes = nodes.stream()
            .sorted(Comparator.comparingInt(AgentNode::order))
            .toList();
    }

    public void execute(AgentPipelineContext context) {
        for (AgentNode node : nodes) {
            Instant started = Instant.now();
            RuntimeException failure = null;
            try {
                node.execute(context);
            } catch (RuntimeException ex) {
                failure = ex;
                throw ex;
            } finally {
                int latencyMs = elapsedMs(started);
                context.recordTiming(node.type(), latencyMs);
                context.recordNodeSpan(
                    node.type(),
                    node.name(),
                    node.order(),
                    failure == null ? "SUCCESS" : "FAILED",
                    latencyMs,
                    failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage()
                );
            }
        }
    }

    public String name() {
        return NAME;
    }

    public List<AgentNode> nodes() {
        return nodes;
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }
}
