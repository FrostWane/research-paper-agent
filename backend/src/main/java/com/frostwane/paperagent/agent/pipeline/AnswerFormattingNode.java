package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.FormatterAgent;
import org.springframework.stereotype.Component;

@Component
public class AnswerFormattingNode implements AgentNode {

    private final FormatterAgent formatterAgent;

    public AnswerFormattingNode(FormatterAgent formatterAgent) {
        this.formatterAgent = formatterAgent;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.FORMATTING;
    }

    @Override
    public String name() {
        return "answer-formatting";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        context.formattedAnswer(formatterAgent.format(context.verifiedAnswer()));
    }
}
