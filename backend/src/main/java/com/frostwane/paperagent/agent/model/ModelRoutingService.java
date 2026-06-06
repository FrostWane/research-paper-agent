package com.frostwane.paperagent.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.model.ModelTargetService.RoutingTarget;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class ModelRoutingService {

    private static final String FALLBACK_TARGET = "fallback-agent";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ModelInvocationRepository modelInvocationRepository;
    private final ModelTargetService modelTargetService;
    private final ObjectMapper objectMapper;

    public ModelRoutingService(
        ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
        ModelInvocationRepository modelInvocationRepository,
        ModelTargetService modelTargetService,
        ObjectMapper objectMapper
    ) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.modelInvocationRepository = modelInvocationRepository;
        this.modelTargetService = modelTargetService;
        this.objectMapper = objectMapper;
    }

    public RoutedAnswer generate(String systemPrompt, String userPrompt, Supplier<String> fallbackSupplier) {
        return generate(ModelTaskType.ANSWER_GENERATION, systemPrompt, userPrompt, fallbackSupplier);
    }

    public RoutedAnswer generate(ModelTaskType taskType, String systemPrompt, String userPrompt, Supplier<String> fallbackSupplier) {
        ModelTaskType requestedTask = taskType == null ? ModelTaskType.ANSWER_GENERATION : taskType;
        List<RoutingTarget> targets = modelTargetService.routingTargets(requestedTask);
        String lastFailure = null;
        for (RoutingTarget target : targets) {
            if ("fallback".equalsIgnoreCase(target.provider())) {
                lastFailure = "Target " + target.code() + " uses fallback provider";
                record(requestedTask, target.provider(), target.modelName(), target.targetName(), "FALLBACK", 0, lastFailure);
                continue;
            }
            Instant started = Instant.now();
            try {
                String content = target.environmentTarget()
                    ? callEnvironmentTarget(systemPrompt, userPrompt)
                    : callOpenAiCompatibleTarget(target, systemPrompt, userPrompt);
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("Model returned empty content");
                }
                record(requestedTask, target.provider(), target.modelName(), target.targetName(), "SUCCESS", elapsedMs(started), null);
                return new RoutedAnswer(content.trim(), target.targetName());
            } catch (Exception ex) {
                lastFailure = "Target " + target.code() + " failed: " + ex.getClass().getSimpleName();
                record(requestedTask, target.provider(), target.modelName(), target.targetName(), "FAILED", elapsedMs(started), ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        return fallback(requestedTask, fallbackSupplier, defaultText(lastFailure, "No model target available"));
    }

    private String callEnvironmentTarget(String systemPrompt, String userPrompt) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("ChatClient unavailable");
        }
        return builder.build()
            .prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();
    }

    private String callOpenAiCompatibleTarget(RoutingTarget target, String systemPrompt, String userPrompt) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(target.timeoutSeconds()))
            .build();
        Map<String, Object> body = Map.of(
            "model", target.modelName(),
            "stream", false,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            )
        );
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(chatCompletionsUrl(target.baseUrl())))
            .timeout(Duration.ofSeconds(target.timeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (target.apiKey() != null && !target.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + target.apiKey().trim());
        }
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + compact(response.body(), 300));
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() ? null : content.asText();
    }

    private RoutedAnswer fallback(ModelTaskType taskType, Supplier<String> fallbackSupplier, String reason) {
        record(taskType, "fallback", FALLBACK_TARGET, FALLBACK_TARGET, "FALLBACK", 0, reason);
        return new RoutedAnswer(fallbackSupplier.get(), FALLBACK_TARGET);
    }

    private void record(ModelTaskType taskType, String provider, String modelName, String targetName, String status, int latencyMs, String errorMessage) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setTaskType((taskType == null ? ModelTaskType.GENERAL : taskType).code());
        invocation.setProvider(defaultText(provider, "fallback"));
        invocation.setModelName(defaultText(modelName, FALLBACK_TARGET));
        invocation.setTargetName(defaultText(targetName, FALLBACK_TARGET));
        invocation.setStatus(status);
        invocation.setLatencyMs(Math.max(0, latencyMs));
        invocation.setErrorMessage(sanitize(errorMessage));
        modelInvocationRepository.save(invocation);
    }

    private String chatCompletionsUrl(String baseUrl) {
        String normalized = defaultText(baseUrl, "https://api.openai.com").replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized.endsWith("/v1") ? normalized + "/chat/completions" : normalized + "/v1/chat/completions";
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    public record RoutedAnswer(String content, String modelName) {
    }
}
