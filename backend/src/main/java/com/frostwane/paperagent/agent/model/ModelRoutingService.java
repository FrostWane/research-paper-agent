package com.frostwane.paperagent.agent.model;

import com.frostwane.paperagent.config.PaperAgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

@Service
public class ModelRoutingService {

    private static final String FALLBACK_TARGET = "fallback-agent";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final PaperAgentProperties properties;
    private final ModelInvocationRepository modelInvocationRepository;

    public ModelRoutingService(
        ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
        PaperAgentProperties properties,
        ModelInvocationRepository modelInvocationRepository
    ) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.properties = properties;
        this.modelInvocationRepository = modelInvocationRepository;
    }

    public RoutedAnswer generate(String systemPrompt, String userPrompt, Supplier<String> fallbackSupplier) {
        String provider = provider();
        String modelName = modelName();
        String targetName = targetName(provider, modelName);

        if ("fallback".equalsIgnoreCase(provider)) {
            return fallback(fallbackSupplier, "AI_PROVIDER=fallback");
        }

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return fallback(fallbackSupplier, "ChatClient unavailable");
        }

        Instant started = Instant.now();
        try {
            String content = builder.build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Model returned empty content");
            }
            record(provider, modelName, targetName, "SUCCESS", elapsedMs(started), null);
            return new RoutedAnswer(content.trim(), targetName);
        } catch (Exception ex) {
            record(provider, modelName, targetName, "FAILED", elapsedMs(started), ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return fallback(fallbackSupplier, "Fallback after " + ex.getClass().getSimpleName());
        }
    }

    private RoutedAnswer fallback(Supplier<String> fallbackSupplier, String reason) {
        record("fallback", FALLBACK_TARGET, FALLBACK_TARGET, "FALLBACK", 0, reason);
        return new RoutedAnswer(fallbackSupplier.get(), FALLBACK_TARGET);
    }

    private void record(String provider, String modelName, String targetName, String status, int latencyMs, String errorMessage) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setProvider(defaultText(provider, "fallback"));
        invocation.setModelName(defaultText(modelName, FALLBACK_TARGET));
        invocation.setTargetName(defaultText(targetName, FALLBACK_TARGET));
        invocation.setStatus(status);
        invocation.setLatencyMs(Math.max(0, latencyMs));
        invocation.setErrorMessage(sanitize(errorMessage));
        modelInvocationRepository.save(invocation);
    }

    private String provider() {
        return defaultText(properties.getAi().getProvider(), "fallback");
    }

    private String modelName() {
        return defaultText(properties.getAi().getChatModel(), "unknown-model");
    }

    private String targetName(String provider, String modelName) {
        if ("fallback".equalsIgnoreCase(provider)) {
            return FALLBACK_TARGET;
        }
        return provider + ":" + modelName;
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
