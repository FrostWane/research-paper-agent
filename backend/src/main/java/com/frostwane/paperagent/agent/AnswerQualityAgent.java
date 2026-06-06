package com.frostwane.paperagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.model.ModelRoutingService;
import com.frostwane.paperagent.agent.model.ModelRoutingService.RoutedAnswer;
import com.frostwane.paperagent.agent.model.ModelTaskType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AnswerQualityAgent {

    private static final Pattern MARKDOWN_STRUCTURE = Pattern.compile("(?m)^(#{1,4}\\s|[-*]\\s|\\d+[.)、]\\s)");
    private static final String FALLBACK_MODEL = "fallback-agent";
    private static final Set<String> QUALITY_LABELS = Set.of(
        "STRONG",
        "GOOD",
        "NEEDS_REVIEW",
        "LOW_EVIDENCE",
        "MATERIAL_LIMITED",
        "EMPTY",
        "UNASSESSED"
    );

    private final ModelRoutingService modelRoutingService;
    private final ObjectMapper objectMapper;

    public AnswerQualityAgent(ModelRoutingService modelRoutingService, ObjectMapper objectMapper) {
        this.modelRoutingService = modelRoutingService;
        this.objectMapper = objectMapper;
    }

    public AnswerQualityAssessment evaluate(String question, String answer, List<SourceResponse> sources) {
        return evaluate(question, answer, sources, false);
    }

    public AnswerQualityAssessment evaluate(String question, String answer, List<SourceResponse> sources, boolean judgeEnabled) {
        AnswerQualityAssessment heuristic = heuristicEvaluate(question, answer, sources);
        if (!judgeEnabled || defaultText(answer, "").isBlank()) {
            return heuristic;
        }

        RoutedAnswer judged = modelRoutingService.generate(
            ModelTaskType.QUALITY_EVALUATION,
            systemPrompt(),
            userPrompt(question, answer, sources, heuristic),
            () -> heuristicJson(heuristic)
        );
        if (FALLBACK_MODEL.equals(judged.modelName())) {
            return heuristic.withJudge(true, "HEURISTIC", 0, judged.modelName(), heuristic.notes());
        }
        try {
            return parseJudge(judged.content(), judged.modelName(), heuristic);
        } catch (Exception ex) {
            String notes = heuristic.notes() + " 模型评审解析失败，已使用启发式结果。";
            return heuristic.withJudge(true, "HEURISTIC_FALLBACK", 0, judged.modelName(), notes);
        }
    }

    private AnswerQualityAssessment heuristicEvaluate(String question, String answer, List<SourceResponse> sources) {
        String normalizedAnswer = defaultText(answer, "");
        if (normalizedAnswer.isBlank()) {
            return new AnswerQualityAssessment(0, "EMPTY", "答案为空，需要重新生成。", "HEURISTIC", 0, false, null);
        }

        List<SourceResponse> safeSources = sources == null ? List.of() : sources;
        boolean materialLimited = containsAny(normalizedAnswer, "材料不足", "证据不足", "缺少检索片段", "无可用检索片段");
        boolean structured = MARKDOWN_STRUCTURE.matcher(normalizedAnswer).find();
        int referencedSources = referencedSources(normalizedAnswer, safeSources);
        int expectedReferences = Math.min(Math.max(safeSources.size(), 1), 5);
        double referenceCoverage = safeSources.isEmpty() ? 0 : referencedSources / (double) expectedReferences;

        int score = 20;
        score += evidenceScore(safeSources, materialLimited, referenceCoverage);
        score += structured ? 15 : 7;
        score += questionCoverageScore(question, normalizedAnswer);
        score += boundaryScore(safeSources, materialLimited);
        score = clamp(score, 0, 100);
        if (safeSources.isEmpty()) {
            score = Math.min(score, materialLimited ? 68 : 40);
        }

        String label = label(score, safeSources, materialLimited);
        return new AnswerQualityAssessment(
            score,
            label,
            notes(safeSources.size(), referencedSources, expectedReferences, structured, materialLimited),
            "HEURISTIC",
            0,
            false,
            null
        );
    }

    private AnswerQualityAssessment parseJudge(String raw, String modelName, AnswerQualityAssessment heuristic) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        int score = clamp(root.path("score").asInt(heuristic.score()), 0, 100);
        String label = normalizeLabel(root.path("label").asText(""), score);
        int confidence = clamp(root.path("confidence").asInt(60), 0, 100);
        String notes = compact(root.path("notes").asText(heuristic.notes()), 700);
        if (notes.isBlank()) {
            notes = heuristic.notes();
        }
        return new AnswerQualityAssessment(score, label, notes, "LLM_JUDGE", confidence, true, modelName);
    }

    private String systemPrompt() {
        return """
            你是科研 RAG 系统的答案质量评审器。
            任务：只根据用户问题、候选答案和检索来源判断答案质量，不补充新事实。
            必须严格返回 JSON，不要输出额外文字：
            {"score":0,"label":"NEEDS_REVIEW","confidence":60,"notes":"一句中文评审说明"}

            评分规则：
            - 重点检查答案是否回答问题、是否忠于来源、是否有证据边界、是否结构清晰。
            - 如果答案使用了来源中没有的信息，应降低分数并标记 NEEDS_REVIEW 或 LOW_EVIDENCE。
            - 如果没有来源但答案明确提示材料不足，可使用 MATERIAL_LIMITED。
            - label 只能是 STRONG、GOOD、NEEDS_REVIEW、LOW_EVIDENCE、MATERIAL_LIMITED、EMPTY。
            - confidence 表示你对本次评审的把握，范围 0-100。
            """;
    }

    private String userPrompt(String question, String answer, List<SourceResponse> sources, AnswerQualityAssessment heuristic) {
        return """
            用户问题：
            %s

            候选答案：
            %s

            检索来源：
            %s

            启发式评估参考：
            score=%d, label=%s, notes=%s
            """.formatted(
            compact(defaultText(question, ""), 1200),
            compact(defaultText(answer, ""), 5000),
            sourceText(sources),
            heuristic.score(),
            heuristic.label(),
            heuristic.notes()
        );
    }

    private String sourceText(List<SourceResponse> sources) {
        List<SourceResponse> safeSources = sources == null ? List.of() : sources;
        if (safeSources.isEmpty()) {
            return "无检索来源。";
        }
        StringBuilder builder = new StringBuilder();
        safeSources.stream().limit(6).forEach(source -> builder
            .append("- 《").append(defaultText(source.title(), "未知文献")).append("》第 ")
            .append(source.page()).append(" 页：")
            .append(compact(defaultText(source.content(), ""), 600))
            .append("\n"));
        return builder.toString();
    }

    private String heuristicJson(AnswerQualityAssessment heuristic) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "score", heuristic.score(),
                "label", heuristic.label(),
                "confidence", 0,
                "notes", heuristic.notes()
            ));
        } catch (Exception ex) {
            return "{\"score\":" + heuristic.score() + ",\"label\":\"" + heuristic.label() + "\",\"confidence\":0,\"notes\":\"" + heuristic.notes().replace("\"", "\\\"") + "\"}";
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

    private int evidenceScore(List<SourceResponse> sources, boolean materialLimited, double referenceCoverage) {
        if (sources.isEmpty()) {
            return materialLimited ? 30 : 8;
        }
        int score = 22 + (int) Math.round(32 * Math.min(1, referenceCoverage));
        return materialLimited ? Math.max(18, score - 12) : score;
    }

    private int boundaryScore(List<SourceResponse> sources, boolean materialLimited) {
        if (sources.isEmpty()) {
            return materialLimited ? 20 : 0;
        }
        return materialLimited ? 6 : 10;
    }

    private int questionCoverageScore(String question, String answer) {
        Set<String> terms = significantTerms(question);
        if (terms.isEmpty()) {
            return answer.length() >= 80 ? 10 : 5;
        }
        String lowerAnswer = answer.toLowerCase(Locale.ROOT);
        long hits = terms.stream().filter(lowerAnswer::contains).count();
        double ratio = hits / (double) terms.size();
        if (ratio >= 0.5) {
            return 15;
        }
        if (ratio > 0) {
            return 9;
        }
        return answer.length() >= 120 ? 6 : 3;
    }

    private int referencedSources(String answer, List<SourceResponse> sources) {
        int referenced = 0;
        String lowerAnswer = answer.toLowerCase(Locale.ROOT);
        for (SourceResponse source : sources) {
            boolean pageMatched = pageMatched(lowerAnswer, source.page());
            boolean titleMatched = source.title() != null && !source.title().isBlank()
                && lowerAnswer.contains(source.title().trim().toLowerCase(Locale.ROOT));
            if (pageMatched || titleMatched) {
                referenced++;
            }
        }
        return referenced;
    }

    private boolean pageMatched(String answer, int page) {
        String value = String.valueOf(page);
        return answer.contains("第 " + value + " 页")
            || answer.contains("第" + value + "页")
            || answer.contains("p" + value)
            || answer.contains("page " + value)
            || answer.contains("页码 " + value);
    }

    private String label(int score, List<SourceResponse> sources, boolean materialLimited) {
        if (sources.isEmpty() && materialLimited) {
            return "MATERIAL_LIMITED";
        }
        if (sources.isEmpty()) {
            return "LOW_EVIDENCE";
        }
        if (score >= 85) {
            return "STRONG";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 50) {
            return "NEEDS_REVIEW";
        }
        return "LOW_EVIDENCE";
    }

    private String normalizeLabel(String value, int score) {
        String normalized = defaultText(value, "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_]+", "_")
            .replaceAll("^_+|_+$", "");
        if (QUALITY_LABELS.contains(normalized) && !"UNASSESSED".equals(normalized)) {
            return normalized;
        }
        if (score >= 85) {
            return "STRONG";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 50) {
            return "NEEDS_REVIEW";
        }
        return "LOW_EVIDENCE";
    }

    private String notes(int sourceCount, int referencedSources, int expectedReferences, boolean structured, boolean materialLimited) {
        StringBuilder builder = new StringBuilder();
        builder.append("来源 ").append(sourceCount).append(" 条");
        if (sourceCount > 0) {
            builder.append("，引用覆盖 ").append(referencedSources).append("/").append(expectedReferences);
        }
        builder.append(structured ? "，结构化输出" : "，结构化较弱");
        if (materialLimited) {
            builder.append("，已提示材料不足");
        }
        return builder.append("。").toString();
    }

    private Set<String> significantTerms(String question) {
        String normalized = defaultText(question, "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ");
        Set<String> terms = new LinkedHashSet<>();
        for (String term : normalized.split("\\s+")) {
            if (term.length() >= 2 && !isStopWord(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStopWord(String value) {
        return value.equals("请")
            || value.equals("一下")
            || value.equals("以及")
            || value.equals("什么")
            || value.equals("哪些")
            || value.equals("如何")
            || value.equals("总结")
            || value.equals("说明");
    }

    private String compact(String value, int maxLength) {
        String trimmed = defaultText(value, "").replaceAll("\\s+", " ").trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    public record AnswerQualityAssessment(
        int score,
        String label,
        String notes,
        String method,
        int confidence,
        boolean judgeEnabled,
        String judgeModelName
    ) {
        public AnswerQualityAssessment withJudge(
            boolean judgeEnabled,
            String method,
            int confidence,
            String judgeModelName,
            String notes
        ) {
            return new AnswerQualityAssessment(
                score,
                label,
                notes,
                method,
                Math.max(0, Math.min(100, confidence)),
                judgeEnabled,
                judgeModelName
            );
        }
    }
}
