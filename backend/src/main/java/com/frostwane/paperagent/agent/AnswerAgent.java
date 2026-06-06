package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.model.ModelRoutingService;
import com.frostwane.paperagent.agent.model.ModelRoutingService.RoutedAnswer;
import com.frostwane.paperagent.agent.prompt.AnswerPromptTemplateService;
import com.frostwane.paperagent.agent.prompt.RenderedAnswerPrompt;
import com.frostwane.paperagent.paper.Paper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnswerAgent {

    private final ModelRoutingService modelRoutingService;
    private final AnswerPromptTemplateService answerPromptTemplateService;

    public AnswerAgent(ModelRoutingService modelRoutingService, AnswerPromptTemplateService answerPromptTemplateService) {
        this.modelRoutingService = modelRoutingService;
        this.answerPromptTemplateService = answerPromptTemplateService;
    }

    public GeneratedAnswer answer(Paper paper, String question, List<SourceResponse> sources) {
        return answer(paper, question, sources, "EVIDENCE_GROUNDED_QA", "");
    }

    public GeneratedAnswer answer(
        Paper paper,
        String question,
        List<SourceResponse> sources,
        String answerStrategy,
        String answerContract
    ) {
        RenderedAnswerPrompt prompt = answerPromptTemplateService.render(paper, question, sources, answerStrategy, answerContract);
        RoutedAnswer answer = modelRoutingService.generate(
            prompt.systemPrompt(),
            prompt.userPrompt(),
            () -> fallbackAnswer(paper, question, sources, answerStrategy)
        );
        return new GeneratedAnswer(answer.content(), answer.modelName());
    }

    private String fallbackAnswer(Paper paper, String question, List<SourceResponse> sources, String answerStrategy) {
        String strategy = defaultText(answerStrategy, "EVIDENCE_GROUNDED_QA");
        String lowerQuestion = question.toLowerCase();
        StringBuilder builder = new StringBuilder();
        builder.append("## 回答\n\n");
        if (paper == null) {
            builder.append("围绕当前文献库，可以先基于已解析片段做如下综合阅读判断：\n\n");
        } else {
            builder.append("围绕《").append(paper.getTitle()).append("》，可以先基于当前题录和已解析片段做如下阅读判断：\n\n");
        }

        if ("EVIDENCE_GAP".equals(strategy)) {
            builder.append("1. **材料状态**：当前没有命中的 PDF 正文片段，材料不足，不能给出可靠结论。\n");
            builder.append("2. **需要补充**：建议先解析 PDF、扩大检索范围，或补充摘要/关键词后再提问。\n");
            builder.append("3. **可追问方向**：可以改问研究问题、方法结构、实验设置或局限性等更具体的问题。\n");
        } else if ("CROSS_PAPER_COMPARISON".equals(strategy)) {
            builder.append("1. **参与比较对象**：优先使用来源片段中出现的论文标题和方法名。\n");
            builder.append("2. **比较维度**：按研究对象、输入特征、模型结构、实验数据、指标结果和结论边界比较。\n");
            builder.append("3. **综述判断**：只基于下方来源片段给出跨论文综合，不把局部证据扩大成全库事实。\n");
        } else if ("REVIEW_SYNTHESIS".equals(strategy)) {
            builder.append("1. **研究主题**：先归纳来源片段共同覆盖的任务和问题域。\n");
            builder.append("2. **方法家族**：再梳理不同论文采用的模型、特征或训练流程。\n");
            builder.append("3. **综述骨架**：最后给出可写入综述的大纲和证据边界。\n");
        } else if ("CONTRIBUTION_ANALYSIS".equals(strategy)
            || lowerQuestion.contains("创新") || lowerQuestion.contains("贡献") || lowerQuestion.contains("contribution")) {
            builder.append("1. **研究问题**：结合标题、关键词和摘要，先确认论文试图解决的核心科研问题。\n");
            builder.append("2. **方法贡献**：重点检查方法章节是否提出新结构、新训练策略、新数据处理流程或新的实验设定。\n");
            builder.append("3. **实证价值**：需要结合实验章节判断提升是否来自充分对比、消融和跨数据集验证。\n");
        } else if ("EXPERIMENT_READING".equals(strategy)
            || lowerQuestion.contains("实验") || lowerQuestion.contains("数据集") || lowerQuestion.contains("evaluation")) {
            builder.append("1. **实验对象**：记录数据集来源、任务设定和样本规模。\n");
            builder.append("2. **对比方法**：检查 baseline 是否覆盖经典方法和最新方法。\n");
            builder.append("3. **评价指标**：关注指标是否贴合研究问题，并核对消融实验是否充分。\n");
        } else if ("LIMITATION_REVIEW".equals(strategy)
            || lowerQuestion.contains("局限") || lowerQuestion.contains("不足") || lowerQuestion.contains("limitation")) {
            builder.append("1. **数据局限**：检查样本规模、场景覆盖和数据偏差。\n");
            builder.append("2. **方法局限**：关注模型假设、计算成本、泛化能力和可解释性。\n");
            builder.append("3. **验证局限**：确认是否缺少真实场景、长期稳定性或跨数据集实验。\n");
        } else if ("STRUCTURED_SUMMARY".equals(strategy)) {
            builder.append("1. **研究问题**：先用一句话定位论文或文献库片段关注的核心问题。\n");
            builder.append("2. **核心方法**：提炼方法流程、关键模块和输入输出。\n");
            builder.append("3. **主要发现**：列出可由来源片段支撑的结论，并标注证据边界。\n");
        } else if (paper == null) {
            builder.append("1. **共同主题**：先查看来源片段覆盖的论文标题，归纳它们围绕的任务、数据和方法家族。\n");
            builder.append("2. **差异维度**：比较模型结构、输入特征、实验数据集、评价指标和结论边界。\n");
            builder.append("3. **证据约束**：综合回答只应使用下方来源片段能支持的信息。\n");
        } else {
            builder.append("1. **题录信息**：作者为 ").append(defaultText(paper.getAuthors(), "未填写")).append("，年份为 ")
                .append(paper.getYear() == null ? "未填写" : paper.getYear()).append("。\n");
            builder.append("2. **关键词线索**：").append(defaultText(paper.getKeywords(), "暂未填写关键词")).append("。\n");
            builder.append("3. **摘要线索**：").append(defaultText(paper.getAbstractText(), "暂未填写摘要，可先完成 PDF 解析后再问答")).append("\n");
        }

        builder.append("\n## 来源\n\n");
        if (sources.isEmpty()) {
            builder.append("材料不足：当前没有命中的 PDF 正文片段。建议先点击解析 PDF，或补充摘要后再提问。\n");
        } else {
            sources.forEach(source -> builder.append("- 《").append(source.title()).append("》第 ").append(source.page()).append(" 页：")
                .append(source.content()).append("\n"));
        }
        return builder.toString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record GeneratedAnswer(String content, String modelName) {
    }
}
