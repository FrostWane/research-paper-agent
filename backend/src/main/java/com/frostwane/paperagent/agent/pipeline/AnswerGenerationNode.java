package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.AnswerAgent;
import com.frostwane.paperagent.agent.AnswerAgent.GeneratedAnswer;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import com.frostwane.paperagent.agent.token.PromptContextBudgetResult;
import com.frostwane.paperagent.agent.token.PromptContextBudgetService;
import org.springframework.stereotype.Component;

@Component
public class AnswerGenerationNode implements AgentNode {

    private final AnswerAgent answerAgent;
    private final RagSettingsService ragSettingsService;
    private final PromptContextBudgetService promptContextBudgetService;

    public AnswerGenerationNode(
        AnswerAgent answerAgent,
        RagSettingsService ragSettingsService,
        PromptContextBudgetService promptContextBudgetService
    ) {
        this.answerAgent = answerAgent;
        this.ragSettingsService = ragSettingsService;
        this.promptContextBudgetService = promptContextBudgetService;
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
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        PromptContextBudgetResult budget = promptContextBudgetService.apply(context.sources(), settings.contextTokenBudget());
        context.sources(budget.sources());
        context.contextTokenBudget(budget.tokenBudget());
        context.contextEstimatedTokens(budget.estimatedTokens());
        context.contextTruncated(budget.truncated());
        GeneratedAnswer generated = answerAgent.answer(
            context.paper(),
            context.question(),
            context.sources(),
            context.conversationHistory(),
            context.toolContext(),
            context.guidanceContext(),
            context.answerStrategy(),
            context.answerContract()
        );
        context.generatedAnswer(generated.content());
        context.modelName(generated.modelName());
    }
}
