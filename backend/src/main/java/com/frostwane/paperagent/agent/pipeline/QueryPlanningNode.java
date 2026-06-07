package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.intent.IntentRouteMatch;
import com.frostwane.paperagent.agent.intent.IntentRouteService;
import com.frostwane.paperagent.agent.term.QueryTermExpansion;
import com.frostwane.paperagent.agent.term.QueryTermMappingService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class QueryPlanningNode implements AgentNode {

    private final IntentRouteService intentRouteService;
    private final QueryTermMappingService queryTermMappingService;

    public QueryPlanningNode(IntentRouteService intentRouteService, QueryTermMappingService queryTermMappingService) {
        this.intentRouteService = intentRouteService;
        this.queryTermMappingService = queryTermMappingService;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.QUERY_PLANNING;
    }

    @Override
    public String name() {
        return "query-planning";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        String question = context.planningQuestion();
        String searchText = context.planningSearchText();
        String normalized = normalize(searchText);
        IntentRouteMatch intent = intentRouteService.match(normalized + " " + normalize(context.question()));
        context.queryIntent(intent.intentCode());
        context.requestedToolNames(intent.boundToolName() == null ? List.of() : List.of(intent.boundToolName()));
        context.comparisonRequested(intent.comparisonRequested());
        String searchQuery = buildSearchQuery(question, normalized, intent.searchHint(), context.libraryScope());
        List<QueryTermExpansion> expansions = queryTermMappingService.match(question, searchQuery);
        context.queryExpansions(expansions);
        context.searchQuery(queryTermMappingService.expandSearchQuery(searchQuery, expansions));
    }

    private String buildSearchQuery(String question, String normalized, String searchHint, boolean libraryScope) {
        String base = normalized
            .replace("请", " ")
            .replace("帮我", " ")
            .replace("一下", " ")
            .replace("这篇论文", " ")
            .replace("这些论文", " ")
            .replace("这些文献", " ")
            .replace("当前文献库", " ")
            .replace("文献库", " ")
            .replace("中这些", " ")
            .replace("的方法", " 方法")
            .replaceAll("(^|\\s)[中的](\\s|$)", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (base.length() < 6) {
            base = question.trim();
        }
        String hint = searchHint == null || searchHint.isBlank() ? "" : " " + searchHint.trim();
        String scopeHint = libraryScope ? " cross paper" : "";
        return (base + hint + scopeHint).trim();
    }

    private String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}，。！？；：、（）【】《》]+", " ").trim();
    }
}
