package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.config.PaperAgentProperties;
import com.frostwane.paperagent.paper.Paper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

@Service
public class AnswerAgent {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final PaperAgentProperties properties;

    public AnswerAgent(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider, PaperAgentProperties properties) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.properties = properties;
    }

    public GeneratedAnswer answer(Paper paper, String question, List<SourceResponse> sources) {
        if (shouldUseModel()) {
            try {
                ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
                if (builder == null) {
                    return new GeneratedAnswer(fallbackAnswer(paper, question, sources), "fallback-agent");
                }
                ChatClient chatClient = builder.build();
                String content = chatClient.prompt()
                    .system(systemPrompt())
                    .user(buildUserPrompt(paper, question, sources))
                    .call()
                    .content();
                if (content != null && !content.isBlank()) {
                    return new GeneratedAnswer(content.trim(), "spring-ai-chat");
                }
            } catch (Exception ignored) {
                // Keep the reading workflow available when model credentials or provider are not ready.
            }
        }
        return new GeneratedAnswer(fallbackAnswer(paper, question, sources), "fallback-agent");
    }

    private boolean shouldUseModel() {
        return !"fallback".equalsIgnoreCase(properties.getAi().getProvider())
            && chatClientBuilderProvider.getIfAvailable() != null;
    }

    private String systemPrompt() {
        return """
            你是 Research Paper Agent 的论文精读 Agent。
            必须基于给定范围、文献题录和检索片段回答。
            如果材料不足，明确说明“材料不足”，不要编造实验结果。
            用结构化中文 Markdown 输出，并尽量附上论文标题和来源页码。
            """;
    }

    private String buildUserPrompt(Paper paper, String question, List<SourceResponse> sources) {
        StringJoiner joiner = new StringJoiner("\n");
        if (paper == null) {
            joiner.add("回答范围：当前用户的整个文献库。");
        } else {
            joiner.add("回答范围：单篇论文精读。");
            joiner.add("文献标题：" + paper.getTitle());
            joiner.add("作者：" + defaultText(paper.getAuthors(), "未填写"));
            joiner.add("会议/期刊：" + defaultText(paper.getVenue(), "未填写"));
            joiner.add("年份：" + (paper.getYear() == null ? "未填写" : paper.getYear()));
            joiner.add("关键词：" + defaultText(paper.getKeywords(), "未填写"));
            joiner.add("摘要：" + defaultText(paper.getAbstractText(), "未填写"));
        }
        joiner.add("用户问题：" + question);
        joiner.add("检索片段：");
        if (sources.isEmpty()) {
            joiner.add("无可用检索片段。");
        } else {
            sources.forEach(source -> joiner.add("- 《" + source.title() + "》第 " + source.page() + " 页：" + source.content()));
        }
        return joiner.toString();
    }

    private String fallbackAnswer(Paper paper, String question, List<SourceResponse> sources) {
        String lowerQuestion = question.toLowerCase();
        StringBuilder builder = new StringBuilder();
        builder.append("## 回答\n\n");
        if (paper == null) {
            builder.append("围绕当前文献库，可以先基于已解析片段做如下综合阅读判断：\n\n");
        } else {
            builder.append("围绕《").append(paper.getTitle()).append("》，可以先基于当前题录和已解析片段做如下阅读判断：\n\n");
        }

        if (lowerQuestion.contains("创新") || lowerQuestion.contains("贡献") || lowerQuestion.contains("contribution")) {
            builder.append("1. **研究问题**：结合标题、关键词和摘要，先确认论文试图解决的核心科研问题。\n");
            builder.append("2. **方法贡献**：重点检查方法章节是否提出新结构、新训练策略、新数据处理流程或新的实验设定。\n");
            builder.append("3. **实证价值**：需要结合实验章节判断提升是否来自充分对比、消融和跨数据集验证。\n");
        } else if (lowerQuestion.contains("实验") || lowerQuestion.contains("数据集") || lowerQuestion.contains("evaluation")) {
            builder.append("1. **实验对象**：记录数据集来源、任务设定和样本规模。\n");
            builder.append("2. **对比方法**：检查 baseline 是否覆盖经典方法和最新方法。\n");
            builder.append("3. **评价指标**：关注指标是否贴合研究问题，并核对消融实验是否充分。\n");
        } else if (lowerQuestion.contains("局限") || lowerQuestion.contains("不足") || lowerQuestion.contains("limitation")) {
            builder.append("1. **数据局限**：检查样本规模、场景覆盖和数据偏差。\n");
            builder.append("2. **方法局限**：关注模型假设、计算成本、泛化能力和可解释性。\n");
            builder.append("3. **验证局限**：确认是否缺少真实场景、长期稳定性或跨数据集实验。\n");
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
