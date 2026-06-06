package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.term.QueryTermExpansion;
import com.frostwane.paperagent.agent.term.QueryTermMappingService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class QueryPlanningNode implements AgentNode {

    private final QueryTermMappingService queryTermMappingService;

    public QueryPlanningNode(QueryTermMappingService queryTermMappingService) {
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
        String question = context.question();
        String normalized = normalize(question);
        String intent = classify(normalized);
        context.queryIntent(intent);
        context.comparisonRequested("COMPARISON".equals(intent) || "REVIEW_SYNTHESIS".equals(intent));
        String searchQuery = buildSearchQuery(question, normalized, intent, context.libraryScope());
        List<QueryTermExpansion> expansions = queryTermMappingService.match(question, searchQuery);
        context.queryExpansions(expansions);
        context.searchQuery(queryTermMappingService.expandSearchQuery(searchQuery, expansions));
    }

    private String classify(String normalized) {
        if (containsAny(normalized, "比较", "差异", "对比", "不同", "compare", "comparison", "difference")) {
            return "COMPARISON";
        }
        if (containsAny(normalized, "综述", "大纲", "研究主题", "研究脉络", "review", "survey", "outline")) {
            return "REVIEW_SYNTHESIS";
        }
        if (containsAny(normalized, "创新", "贡献", "contribution", "novelty")) {
            return "CONTRIBUTION";
        }
        if (containsAny(normalized, "实验", "数据集", "指标", "结果", "消融", "evaluation", "experiment", "dataset", "benchmark")) {
            return "EXPERIMENT";
        }
        if (containsAny(normalized, "局限", "不足", "限制", "未来", "limitation", "future work")) {
            return "LIMITATION";
        }
        if (containsAny(normalized, "总结", "概括", "摘要", "summary", "summarize")) {
            return "SUMMARY";
        }
        return "GENERAL_QA";
    }

    private String buildSearchQuery(String question, String normalized, String intent, boolean libraryScope) {
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
        String hint = switch (intent) {
            case "COMPARISON" -> " method dataset result difference";
            case "REVIEW_SYNTHESIS" -> " method task dataset conclusion";
            case "CONTRIBUTION" -> " contribution method innovation";
            case "EXPERIMENT" -> " experiment dataset metric result baseline";
            case "LIMITATION" -> " limitation future work";
            case "SUMMARY" -> " abstract method result conclusion";
            default -> "";
        };
        String scopeHint = libraryScope ? " cross paper" : "";
        return (base + hint + scopeHint).trim();
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}，。！？；：、（）【】《》]+", " ").trim();
    }
}
