package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AnswerQualityAgent {

    private static final Pattern MARKDOWN_STRUCTURE = Pattern.compile("(?m)^(#{1,4}\\s|[-*]\\s|\\d+[.)、]\\s)");

    public AnswerQualityAssessment evaluate(String question, String answer, List<SourceResponse> sources) {
        String normalizedAnswer = defaultText(answer, "");
        if (normalizedAnswer.isBlank()) {
            return new AnswerQualityAssessment(0, "EMPTY", "答案为空，需要重新生成。");
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
            notes(safeSources.size(), referencedSources, expectedReferences, structured, materialLimited)
        );
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    public record AnswerQualityAssessment(
        int score,
        String label,
        String notes
    ) {
    }
}
