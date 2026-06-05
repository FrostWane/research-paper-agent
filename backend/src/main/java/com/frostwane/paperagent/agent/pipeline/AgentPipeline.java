package com.frostwane.paperagent.agent.pipeline;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class AgentPipeline {

    private final List<AgentNode> nodes;

    public AgentPipeline(List<AgentNode> nodes) {
        this.nodes = nodes.stream()
            .sorted(Comparator.comparingInt(AgentNode::order))
            .toList();
    }

    public void execute(AgentPipelineContext context) {
        for (AgentNode node : nodes) {
            Instant started = Instant.now();
            try {
                node.execute(context);
            } finally {
                context.recordTiming(node.type(), elapsedMs(started));
            }
        }
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }
}
