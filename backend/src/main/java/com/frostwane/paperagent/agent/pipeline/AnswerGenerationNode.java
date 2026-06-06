package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.AnswerAgent;
import com.frostwane.paperagent.agent.AnswerAgent.GeneratedAnswer;
import org.springframework.stereotype.Component;

@Component
public class AnswerGenerationNode implements AgentNode {

    private final AnswerAgent answerAgent;

    public AnswerGenerationNode(AnswerAgent answerAgent) {
        this.answerAgent = answerAgent;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.GENERATION;
    }

    @Override
    public String name() {
        return "answer-generation";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        GeneratedAnswer generated = answerAgent.answer(
            context.paper(),
            context.question(),
            context.sources(),
            context.conversationHistory(),
            context.toolContext(),
            context.answerStrategy(),
            context.answerContract()
        );
        context.generatedAnswer(generated.content());
        context.modelName(generated.modelName());
    }
}
