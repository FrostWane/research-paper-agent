package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.rewrite.QueryRewriteAgent;
import com.frostwane.paperagent.agent.rewrite.QueryRewriteResult;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import org.springframework.stereotype.Component;

@Component
public class QueryRewriteNode implements AgentNode {

    private final QueryRewriteAgent queryRewriteAgent;
    private final RagSettingsService ragSettingsService;

    public QueryRewriteNode(QueryRewriteAgent queryRewriteAgent, RagSettingsService ragSettingsService) {
        this.queryRewriteAgent = queryRewriteAgent;
        this.ragSettingsService = ragSettingsService;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.QUERY_REWRITE;
    }

    @Override
    public String name() {
        return "query-rewrite-and-split";
    }

    @Override
    public int order() {
        return 14;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        context.queryRewriteEnabled(settings.queryRewriteEnabled());
        if (!settings.queryRewriteEnabled()) {
            context.rewrittenQuery(context.question());
            context.querySubQuestions(java.util.List.of(context.question()));
            context.searchQuery(context.question());
            return;
        }
        QueryRewriteResult result = queryRewriteAgent.rewrite(
            context.question(),
            context.conversationHistory(),
            context.libraryScope(),
            settings.queryRewriteMaxSubQuestions()
        );
        context.rewrittenQuery(result.rewrittenQuery());
        context.querySubQuestions(result.subQuestions());
        context.queryRewriteModelName(result.modelName());
        context.searchQuery(result.rewrittenQuery());
    }
}
