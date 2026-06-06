package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.model.ModelRoutingService;
import com.frostwane.paperagent.agent.model.ModelTaskType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationSummaryAgent {

    private static final String FALLBACK_TARGET = "fallback-agent";

    private final ModelRoutingService modelRoutingService;

    public ConversationSummaryAgent(ModelRoutingService modelRoutingService) {
        this.modelRoutingService = modelRoutingService;
    }

    public SummaryResult summarize(String existingSummary, List<ChatRecord> records, int maxChars) {
        if (records == null || records.isEmpty()) {
            return new SummaryResult(compact(existingSummary, maxChars), "HEURISTIC", FALLBACK_TARGET);
        }
        String fallback = heuristicSummary(existingSummary, records, maxChars);
        ModelRoutingService.RoutedAnswer routed = modelRoutingService.generate(
            ModelTaskType.CONVERSATION_SUMMARY,
            systemPrompt(),
            userPrompt(existingSummary, records, maxChars),
            () -> fallback
        );
        String content = compact(cleanSummary(routed.content()), maxChars);
        if (content == null || content.isBlank()) {
            content = fallback;
        }
        String method = FALLBACK_TARGET.equals(routed.modelName()) ? "HEURISTIC_FALLBACK" : "LLM_SUMMARY";
        return new SummaryResult(content, method, routed.modelName());
    }

    private String systemPrompt() {
        return """
            你是 Research Paper Agent 的会话记忆压缩器。
            任务是维护一份长期会话摘要，供后续科研问答继续引用。
            摘要必须保留用户目标、已经讨论过的论文/方法、关键结论、偏好、约束和未解决问题。
            不要添加对话中没有出现的新事实；不要输出寒暄；使用中文，结构紧凑。
            """;
    }

    private String userPrompt(String existingSummary, List<ChatRecord> records, int maxChars) {
        return """
            已有长期摘要：
            %s

            本次需要合并的旧对话：
            %s

            请输出更新后的长期摘要，不超过 %d 个中文字符。建议使用 3-6 条要点。
            """.formatted(
            existingSummary == null || existingSummary.isBlank() ? "无" : existingSummary.trim(),
            transcript(records),
            Math.max(300, maxChars)
        );
    }

    private String transcript(List<ChatRecord> records) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (ChatRecord record : records) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("第 ").append(index++).append(" 轮用户：")
                .append(compact(record.getQuestion(), 500))
                .append("\n第 ").append(index - 1).append(" 轮助手：")
                .append(compact(record.getAnswer(), 900));
        }
        return builder.toString();
    }

    private String heuristicSummary(String existingSummary, List<ChatRecord> records, int maxChars) {
        StringBuilder builder = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            builder.append(existingSummary.trim());
        }
        for (ChatRecord record : records) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- 用户关注：")
                .append(compact(record.getQuestion(), 140))
                .append("；已有回答要点：")
                .append(compact(record.getAnswer(), 260));
        }
        return compact(builder.toString(), maxChars);
    }

    private String cleanSummary(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replaceAll("(?i)^```(?:text|markdown)?", "")
            .replaceAll("```$", "")
            .trim();
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    public record SummaryResult(
        String content,
        String method,
        String modelName
    ) {
    }
}
