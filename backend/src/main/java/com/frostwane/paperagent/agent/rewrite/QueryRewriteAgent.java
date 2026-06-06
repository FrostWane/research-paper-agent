package com.frostwane.paperagent.agent.rewrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.model.ModelRoutingService;
import com.frostwane.paperagent.agent.model.ModelRoutingService.RoutedAnswer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryRewriteAgent {

    private final ModelRoutingService modelRoutingService;
    private final ObjectMapper objectMapper;

    public QueryRewriteAgent(ModelRoutingService modelRoutingService, ObjectMapper objectMapper) {
        this.modelRoutingService = modelRoutingService;
        this.objectMapper = objectMapper;
    }

    public QueryRewriteResult rewrite(String question, String conversationHistory, boolean libraryScope, int maxSubQuestions) {
        String normalizedQuestion = defaultText(question, "");
        RoutedAnswer answer = modelRoutingService.generate(
            systemPrompt(),
            userPrompt(normalizedQuestion, conversationHistory, libraryScope, maxSubQuestions),
            () -> fallbackJson(normalizedQuestion)
        );
        return parse(answer.content(), normalizedQuestion, answer.modelName(), maxSubQuestions);
    }

    private QueryRewriteResult parse(String raw, String question, String modelName, int maxSubQuestions) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            String rewrite = root.path("rewrite").asText("").trim();
            if (rewrite.isBlank()) {
                rewrite = question;
            }
            List<String> subQuestions = new ArrayList<>();
            JsonNode subs = root.path("sub_questions");
            if (subs.isArray()) {
                for (JsonNode sub : subs) {
                    String value = sub.asText("").trim();
                    if (!value.isBlank() && subQuestions.stream().noneMatch(value::equalsIgnoreCase)) {
                        subQuestions.add(compact(value, 800));
                    }
                    if (subQuestions.size() >= maxSubQuestions) {
                        break;
                    }
                }
            }
            if (subQuestions.isEmpty()) {
                subQuestions.add(rewrite);
            }
            return new QueryRewriteResult(compact(rewrite, 1200), subQuestions, modelName);
        } catch (Exception ex) {
            return new QueryRewriteResult(question, List.of(question), modelName);
        }
    }

    private String extractJson(String raw) {
        String text = defaultText(raw, "{}").trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String fallbackJson(String question) {
        try {
            return objectMapper.writeValueAsString(new FallbackRewrite(question, false, List.of(question)));
        } catch (Exception ex) {
            return "{\"rewrite\":\"" + question.replace("\"", "\\\"") + "\",\"should_split\":false,\"sub_questions\":[]}";
        }
    }

    private String systemPrompt() {
        return """
            你是用于科研文献 RAG 检索阶段的查询改写助手。
            任务：把用户问题改写成更适合检索的自然语言查询，并判断是否需要拆分子问题。
            必须严格返回 JSON，不要输出额外文字：
            {"rewrite":"改写后的查询","should_split":false,"sub_questions":["子问题1"]}

            规则：
            - 保留论文标题、方法名、数据集、模型名、指标、时间范围等专有名词。
            - 删除“请帮我”“详细说明”“分点回答”等对检索无帮助的表达。
            - 不得添加原问题没有的事实、假设、实验结果或条件。
            - 保持原问题语言；中文问题用中文，英文问题用英文。
            - 指代词可以结合历史对话还原具体对象。
            - 只有多个独立问题、显式列举、分号或换行分隔时才拆分。
            - 抽象对比问题不要拆分。
            """;
    }

    private String userPrompt(String question, String conversationHistory, boolean libraryScope, int maxSubQuestions) {
        return """
            问答范围：%s
            子问题数量上限：%d
            历史对话：
            %s

            用户问题：%s
            """.formatted(
            libraryScope ? "当前用户的整个文献库" : "单篇论文",
            Math.max(1, maxSubQuestions),
            defaultText(conversationHistory, "无历史对话。"),
            question
        );
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record FallbackRewrite(String rewrite, boolean should_split, List<String> sub_questions) {
    }
}
