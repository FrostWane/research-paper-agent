package com.frostwane.paperagent.agent.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class IntentGuidanceNode implements AgentNode {

    @Override
    public AgentNodeType type() {
        return AgentNodeType.INTENT_GUIDANCE;
    }

    @Override
    public String name() {
        return "intent-guidance";
    }

    @Override
    public int order() {
        return 24;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        if (isVagueQuestion(context)) {
            context.guidance(
                "AMBIGUOUS_QUESTION",
                context.libraryScope()
                    ? "当前问题比较宽泛，建议先限定主题、论文集合或分析维度。"
                    : "当前问题比较宽泛，建议先限定阅读维度再进入正文分析。",
                "问题只给出泛化动作或泛化对象，缺少研究问题、方法、实验、贡献或局限等具体维度。",
                suggestions(context)
            );
            return;
        }

        if (context.useRag() && context.sourceCount() == 0 && !hasSuccessfulTool(context)) {
            context.guidance(
                context.libraryScope() ? "LIBRARY_EVIDENCE_GAP" : "PAPER_EVIDENCE_GAP",
                context.libraryScope()
                    ? "当前全库检索没有命中可引用片段，建议先补充主题或检查文献解析状态。"
                    : "当前论文检索没有命中可引用片段，建议先解析 PDF 或换成更具体的问题。",
                "RAG 检索没有可用来源片段，且没有成功的业务工具结果可支撑直接回答。",
                evidenceGapSuggestions(context)
            );
        }
    }

    private boolean isVagueQuestion(AgentPipelineContext context) {
        String compact = compact(context.question());
        if (compact.isBlank()) {
            return true;
        }
        if (compact.length() > 28) {
            return false;
        }
        if (List.of("总结一下", "介绍一下", "讲讲", "说说", "分析一下", "帮我看看", "怎么看", "这篇论文", "这些论文", "这些文献", "文献库")
            .stream()
            .anyMatch(compact::contains)) {
            return true;
        }
        if (compact.length() <= 12 && List.of("总结", "概括", "摘要", "介绍", "分析", "综述", "summary", "overview", "explain")
            .stream()
            .anyMatch(compact::contains)) {
            return true;
        }
        return "SUMMARY".equals(context.queryIntent()) && compact.length() <= 12;
    }

    private boolean hasSuccessfulTool(AgentPipelineContext context) {
        return context.toolExecutions().stream().anyMatch(item -> "SUCCESS".equals(item.status()));
    }

    private List<String> suggestions(AgentPipelineContext context) {
        if (context.libraryScope()) {
            return List.of(
                "请围绕一个具体主题或关键词，梳理当前文献库的方法家族和证据分布。",
                "请比较当前文献库中某一主题的代表论文，维度包括研究对象、方法、数据和局限。",
                "请统计当前文献库的解析状态和可用知识片段，再给出下一步阅读建议。"
            );
        }
        return List.of(
            "请按研究问题、核心方法、实验设置和主要结论总结这篇论文。",
            "请提炼这篇论文的 2-4 个贡献点，并标注证据页码。",
            "请整理这篇论文的实验数据集、baseline、评价指标和关键结果。"
        );
    }

    private List<String> evidenceGapSuggestions(AgentPipelineContext context) {
        if (context.libraryScope()) {
            return List.of(
                "请先检查当前文献库中哪些论文已解析，并告诉我可用于问答的知识片段数量。",
                "请把问题改成更具体的主题、方法名或关键词后重新检索。",
                "请先解析相关 PDF，再围绕具体主题做跨论文比较或综述。"
            );
        }
        return List.of(
            "请先解析这篇论文的 PDF，然后再问研究问题、方法或实验细节。",
            "请把问题改成更具体的章节或概念，例如方法结构、实验设置或局限性。",
            "请补充论文标题、摘要或关键词线索后，再要求我做结构化总结。"
        );
    }

    private String compact(String value) {
        return (value == null ? "" : value)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{Punct}，。！？；：、（）【】《》\\s]+", "")
            .trim();
    }
}
