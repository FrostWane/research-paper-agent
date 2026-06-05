package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentPipelineContext {

    private final ChatRequest request;
    private final User owner;
    private final String question;
    private final String scope;
    private final Map<AgentNodeType, Integer> timings = new EnumMap<>(AgentNodeType.class);
    private final List<NodeSpan> nodeSpans = new ArrayList<>();

    private Paper paper;
    private List<SourceResponse> sources = List.of();
    private String generatedAnswer;
    private String verifiedAnswer;
    private String formattedAnswer;
    private String modelName;

    public AgentPipelineContext(ChatRequest request, User owner) {
        this.request = request;
        this.owner = owner;
        this.question = request.question().trim();
        this.scope = request.paperId() == null ? "LIBRARY" : "PAPER";
    }

    public ChatRequest request() {
        return request;
    }

    public User owner() {
        return owner;
    }

    public String question() {
        return question;
    }

    public String scope() {
        return scope;
    }

    public boolean libraryScope() {
        return request.paperId() == null;
    }

    public boolean useRag() {
        return request.useRag();
    }

    public Paper paper() {
        return paper;
    }

    public void paper(Paper paper) {
        this.paper = paper;
    }

    public List<SourceResponse> sources() {
        return sources;
    }

    public void sources(List<SourceResponse> sources) {
        this.sources = sources == null ? List.of() : sources;
    }

    public int sourceCount() {
        return sources.size();
    }

    public String generatedAnswer() {
        return generatedAnswer;
    }

    public void generatedAnswer(String generatedAnswer) {
        this.generatedAnswer = generatedAnswer;
    }

    public String verifiedAnswer() {
        return verifiedAnswer;
    }

    public void verifiedAnswer(String verifiedAnswer) {
        this.verifiedAnswer = verifiedAnswer;
    }

    public String formattedAnswer() {
        return formattedAnswer;
    }

    public void formattedAnswer(String formattedAnswer) {
        this.formattedAnswer = formattedAnswer;
    }

    public String modelName() {
        return modelName;
    }

    public void modelName(String modelName) {
        this.modelName = modelName;
    }

    public void recordTiming(AgentNodeType type, int latencyMs) {
        timings.put(type, latencyMs);
    }

    public int timingMs(AgentNodeType type) {
        return timings.getOrDefault(type, 0);
    }

    public void recordNodeSpan(AgentNodeType type, String name, int order, String status, int durationMs, String errorMessage) {
        nodeSpans.add(new NodeSpan(
            type.name(),
            name,
            order,
            status,
            Math.max(0, durationMs),
            sanitizeError(errorMessage)
        ));
    }

    public List<NodeSpan> nodeSpans() {
        return List.copyOf(nodeSpans);
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 600 ? sanitized.substring(0, 600) : sanitized;
    }

    public record NodeSpan(
        String type,
        String name,
        int order,
        String status,
        int durationMs,
        String errorMessage
    ) {
    }
}
