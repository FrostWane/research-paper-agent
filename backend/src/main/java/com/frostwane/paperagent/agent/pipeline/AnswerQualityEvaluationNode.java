package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.AnswerQualityAgent;
import com.frostwane.paperagent.agent.AnswerQualityAgent.AnswerQualityAssessment;
import org.springframework.stereotype.Component;

@Component
public class AnswerQualityEvaluationNode implements AgentNode {

    private final AnswerQualityAgent answerQualityAgent;

    public AnswerQualityEvaluationNode(AnswerQualityAgent answerQualityAgent) {
        this.answerQualityAgent = answerQualityAgent;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.EVALUATION;
    }

    @Override
    public String name() {
        return "answer-quality-evaluation";
    }

    @Override
    public int order() {
        return 45;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        AnswerQualityAssessment assessment = answerQualityAgent.evaluate(
            context.question(),
            context.verifiedAnswer(),
            context.sources()
        );
        context.answerQualityScore(assessment.score());
        context.answerQualityLabel(assessment.label());
        context.answerQualityNotes(assessment.notes());
    }
}
