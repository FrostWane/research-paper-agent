package com.frostwane.paperagent.embedding;

import com.frostwane.paperagent.config.PaperAgentProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmbeddingService {

    public static final int DIMENSIONS = 1536;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+|\\p{IsHan}");
    private static final String LOCAL_PROVIDER_ID = "local-hash-v1";

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final PaperAgentProperties properties;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider, PaperAgentProperties properties) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.properties = properties;
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (shouldUseRemoteEmbedding()) {
            try {
                EmbeddingModel model = embeddingModelProvider.getIfAvailable();
                if (model != null) {
                    return model.embed(texts).stream()
                        .map(this::fitDimensions)
                        .toList();
                }
            } catch (Exception ignored) {
                // Fall through to the deterministic local embedder so parsing and RAG stay available.
            }
        }
        return texts.stream().map(this::localEmbedding).toList();
    }

    public String providerId() {
        if (shouldUseRemoteEmbedding() && embeddingModelProvider.getIfAvailable() != null) {
            return "spring-ai-embedding";
        }
        return LOCAL_PROVIDER_ID;
    }

    private boolean shouldUseRemoteEmbedding() {
        String provider = properties.getAi().getEmbeddingProvider();
        return !"local".equalsIgnoreCase(provider)
            && !"fallback".equalsIgnoreCase(properties.getAi().getProvider());
    }

    private float[] localEmbedding(String text) {
        float[] vector = new float[DIMENSIONS];
        List<String> tokens = tokenize(text);
        for (String token : tokens) {
            addFeature(vector, "tok:" + token, featureWeight(token));
            if (token.length() > 5) {
                addFeature(vector, "pre:" + token.substring(0, Math.min(token.length(), 8)), 0.55f);
            }
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            addFeature(vector, "bi:" + tokens.get(i) + "_" + tokens.get(i + 1), 0.7f);
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private float featureWeight(String token) {
        return (float) (1.0d + Math.min(2.0d, Math.log(Math.max(1, token.length()))));
    }

    private void addFeature(float[] vector, String feature, float weight) {
        long hash = fnv64(feature);
        int index = (int) Math.floorMod(hash, DIMENSIONS);
        float sign = ((hash >>> 63) == 0) ? 1.0f : -1.0f;
        vector[index] += sign * weight;
    }

    private long fnv64(String value) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private float[] fitDimensions(float[] source) {
        float[] vector = Arrays.copyOf(source, DIMENSIONS);
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
