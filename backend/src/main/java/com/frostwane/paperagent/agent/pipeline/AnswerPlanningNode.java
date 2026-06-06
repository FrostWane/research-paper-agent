package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.intent.IntentRouteMatch;
import com.frostwane.paperagent.agent.intent.IntentRouteService;
import org.springframework.stereotype.Component;

@Component
public class AnswerPlanningNode implements AgentNode {

    private final IntentRouteService intentRouteService;

    public AnswerPlanningNode(IntentRouteService intentRouteService) {
        this.intentRouteService = intentRouteService;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.ANSWER_PLANNING;
    }

    @Override
    public String name() {
        return "answer-planning";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        IntentRouteMatch route = intentRouteService.resolve(context.queryIntent());
        String strategy = resolveStrategy(context, route);
        context.answerStrategy(strategy);
        context.answerContract(buildContract(strategy, context.libraryScope(), route.answerContract()));
    }

    private String resolveStrategy(AgentPipelineContext context, IntentRouteMatch route) {
        if (context.sourceCount() == 0) {
            return "EVIDENCE_GAP";
        }
        return route.answerStrategy();
    }

    private String buildContract(String strategy, boolean libraryScope, String customContract) {
        String scopeRule = libraryScope
            ? "回答必须先判断证据覆盖了哪些论文，不要把单篇证据扩展成全库结论。"
            : "回答必须聚焦当前论文，不要引入检索片段之外的论文事实。";
        String evidenceRule = "每个关键结论都要能对应到检索片段；证据不足时明确写出边界。";
        String strategyRule = customContract == null || customContract.isBlank() ? defaultStrategyRule(strategy) : customContract.trim();
        return (scopeRule + "\n" + evidenceRule + "\n" + strategyRule).trim();
    }

    private String defaultStrategyRule(String strategy) {
        return switch (strategy) {
            case "CROSS_PAPER_COMPARISON" -> """
                输出结构：先列出参与比较的论文/方法；再用 Markdown 表格比较“研究对象、方法结构、数据/实验、优势、局限”；最后给出适合综述写作的综合判断。
                """;
            case "REVIEW_SYNTHESIS" -> """
                输出结构：按“研究主题、方法家族、证据地图、争议/空白、综述大纲”组织，不要只做逐篇摘要。
                """;
            case "CONTRIBUTION_ANALYSIS" -> """
                输出结构：提炼 2-4 个贡献点；每个贡献点说明解决的问题、技术抓手、证据片段和可能边界。
                """;
            case "EXPERIMENT_READING" -> """
                输出结构：优先整理数据集、baseline、指标、主要结果和消融/鲁棒性信息；适合时使用表格。
                """;
            case "LIMITATION_REVIEW" -> """
                输出结构：从数据、方法假设、实验验证、泛化与应用风险四个角度分析局限，并给出后续研究建议。
                """;
            case "STRUCTURED_SUMMARY" -> """
                输出结构：用“研究问题、核心方法、主要发现、证据页码、可追问点”做紧凑摘要。
                """;
            case "EVIDENCE_GAP" -> """
                输出结构：先说明材料不足；列出缺失的证据类型；给出下一步解析、补充文献或重新提问建议。
                """;
            default -> """
                输出结构：直接回答问题；必要时拆成要点；结尾列出证据来源和不确定性。
                """;
        };
    }
}
