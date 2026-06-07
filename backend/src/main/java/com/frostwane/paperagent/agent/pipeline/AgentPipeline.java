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
    private final AgentPipelineNodeSettingService nodeSettingService;

    public AgentPipeline(List<AgentNode> nodes, AgentPipelineNodeSettingService nodeSettingService) {
        this.nodes = nodes.stream()
            .sorted(Comparator.comparingInt(AgentNode::order))
            .toList();
        this.nodeSettingService = nodeSettingService;
    }

    public void execute(AgentPipelineContext context) {
        for (AgentNode node : nodes) {
            if (!nodeSettingService.enabled(node)) {
                handleDisabledNode(node, context);
                context.recordTiming(node.type(), 0);
                context.recordNodeSpan(node.type(), node.name(), node.order(), "SKIPPED", 0, "节点已在管理后台停用");
                continue;
            }
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

    private void handleDisabledNode(AgentNode node, AgentPipelineContext context) {
        if (node.type() == AgentNodeType.VERIFICATION) {
            context.verifiedAnswer(context.generatedAnswer());
        }
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }
}
