package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.AnswerQualityAgent;
import com.frostwane.paperagent.agent.AnswerQualityAgent.AnswerQualityAssessment;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import org.springframework.stereotype.Component;

@Component
public class AnswerQualityEvaluationNode implements AgentNode {

    private final AnswerQualityAgent answerQualityAgent;
    private final RagSettingsService ragSettingsService;

    public AnswerQualityEvaluationNode(AnswerQualityAgent answerQualityAgent, RagSettingsService ragSettingsService) {
        this.answerQualityAgent = answerQualityAgent;
        this.ragSettingsService = ragSettingsService;
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
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        AnswerQualityAssessment assessment = answerQualityAgent.evaluate(
            context.question(),
            context.verifiedAnswer(),
            context.sources(),
            settings.answerQualityJudgeEnabled()
        );
        context.answerQualityScore(assessment.score());
        context.answerQualityLabel(assessment.label());
        context.answerQualityNotes(assessment.notes());
        context.answerQualityMethod(assessment.method());
        context.answerQualityJudgeEnabled(assessment.judgeEnabled());
        context.answerQualityJudgeModelName(assessment.judgeModelName());
        context.answerQualityConfidence(assessment.confidence());
    }
}
