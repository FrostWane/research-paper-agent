package com.frostwane.paperagent.agent.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.model.ModelRoutingService;
import com.frostwane.paperagent.agent.model.ModelRoutingService.RoutedAnswer;
import com.frostwane.paperagent.agent.model.ModelTaskType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class ModelRerankAgent {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private final ModelRoutingService modelRoutingService;
    private final ObjectMapper objectMapper;

    public ModelRerankAgent(ModelRoutingService modelRoutingService, ObjectMapper objectMapper) {
        this.modelRoutingService = modelRoutingService;
        this.objectMapper = objectMapper;
    }

    public List<Integer> rerank(String query, List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        RoutedAnswer answer = modelRoutingService.generate(
            ModelTaskType.RETRIEVAL_RERANK,
            systemPrompt(),
            userPrompt(query, candidates),
            () -> fallbackJson(candidates.size())
        );
        return parseRanking(answer.content(), candidates.size());
    }

    List<Integer> parseRanking(String raw, int candidateCount) {
        if (candidateCount <= 0) {
            return List.of();
        }
        List<Integer> parsed = parseJsonRanking(raw);
        if (parsed.isEmpty()) {
            parsed = parseLooseRanking(raw);
        }
        return normalize(parsed, candidateCount);
    }

    private List<Integer> parseJsonRanking(String raw) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            JsonNode ranking = rankingNode(root);
            if (!ranking.isArray()) {
                return List.of();
            }
            List<Integer> indexes = new ArrayList<>();
            for (JsonNode node : ranking) {
                Integer value = indexValue(node);
                if (value != null) {
                    indexes.add(value);
                }
            }
            return indexes;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private JsonNode rankingNode(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("rankedIndexes", "ranked_indices", "ranking", "indices", "indexes")) {
            JsonNode node = root.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return objectMapper.createArrayNode();
    }

    private Integer indexValue(JsonNode node) {
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            return parseInteger(node.asText());
        }
        if (node.isObject()) {
            for (String field : List.of("index", "idx", "candidateIndex", "candidate_index")) {
                JsonNode value = node.path(field);
                if (!value.isMissingNode()) {
                    return indexValue(value);
                }
            }
        }
        return null;
    }

    private List<Integer> parseLooseRanking(String raw) {
        List<Integer> indexes = new ArrayList<>();
        Matcher matcher = INTEGER_PATTERN.matcher(defaultText(raw, ""));
        while (matcher.find()) {
            Integer value = parseInteger(matcher.group());
            if (value != null) {
                indexes.add(value);
            }
        }
        return indexes;
    }

    private List<Integer> normalize(List<Integer> indexes, int candidateCount) {
        boolean oneBased = !indexes.isEmpty()
            && indexes.stream().noneMatch(index -> index == 0)
            && indexes.stream().anyMatch(index -> index == candidateCount)
            && indexes.stream().allMatch(index -> index >= 1 && index <= candidateCount);

        Set<Integer> selected = new LinkedHashSet<>();
        for (Integer value : indexes) {
            int index = oneBased ? value - 1 : value;
            if (index >= 0 && index < candidateCount) {
                selected.add(index);
            }
        }
        IntStream.range(0, candidateCount)
            .filter(index -> !selected.contains(index))
            .forEach(selected::add);
        return List.copyOf(selected);
    }

    private String extractJson(String raw) {
        String text = defaultText(raw, "[]").trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return text;
    }

    private String systemPrompt() {
        return """
            你是科研文献 RAG 的候选片段重排器。
            任务：只根据用户问题和候选片段相关性，对候选片段从最相关到最不相关排序。
            必须严格返回 JSON，不要输出额外文字：
            {"rankedIndexes":[0,1,2]}

            规则：
            - rankedIndexes 使用候选片段的 0 基 index。
            - 不要编造候选片段中没有的事实。
            - 优先选择能直接回答问题、包含方法/实验/结论细节、与范围匹配的片段。
            - 如果相关性接近，保留原始顺序。
            """;
    }

    private String userPrompt(String query, List<RetrievalCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate candidate = candidates.get(i);
            builder
                .append("[").append(i).append("] ")
                .append("标题：").append(compact(candidate.title(), 160)).append("\n")
                .append("页码：").append(candidate.pageNumber()).append("，通道：")
                .append(defaultText(candidate.channelName(), "unknown")).append("，分数：")
                .append(String.format(java.util.Locale.ROOT, "%.4f", candidate.score())).append("\n")
                .append("片段：").append(compact(candidate.content(), 520)).append("\n\n");
        }
        return """
            用户问题：
            %s

            候选片段：
            %s
            """.formatted(compact(query, 1000), builder.toString().trim());
    }

    private String fallbackJson(int candidateCount) {
        String indexes = IntStream.range(0, Math.max(0, candidateCount))
            .mapToObj(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        return "{\"rankedIndexes\":[" + indexes + "]}";
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(defaultText(value, "").replaceAll("[^\\d-]", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private String compact(String value, int maxLength) {
        String normalized = defaultText(value, "").replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
