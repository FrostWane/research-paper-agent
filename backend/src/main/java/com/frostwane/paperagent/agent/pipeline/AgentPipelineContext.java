package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.retrieval.RetrievalChannelTrace;
import com.frostwane.paperagent.agent.retrieval.RetrievalProcessorTrace;
import com.frostwane.paperagent.agent.term.QueryTermExpansion;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AgentPipelineContext {

    private final ChatRequest request;
    private final User owner;
    private final String question;
    private final String scope;
    private final Map<AgentNodeType, Integer> timings = new EnumMap<>(AgentNodeType.class);
    private final List<NodeSpan> nodeSpans = new ArrayList<>();

    private Paper paper;
    private List<SourceResponse> sources = List.of();
    private List<RetrievalChannelTrace> retrievalChannels = List.of();
    private List<RetrievalProcessorTrace> retrievalProcessors = List.of();
    private List<QueryTermExpansion> queryExpansions = List.of();
    private String queryIntent = "GENERAL_QA";
    private String searchQuery;
    private boolean queryRewriteEnabled;
    private String rewrittenQuery;
    private List<String> querySubQuestions = List.of();
    private String queryRewriteModelName;
    private boolean comparisonRequested;
    private String answerStrategy = "EVIDENCE_GROUNDED_QA";
    private String answerContract = "";
    private String generatedAnswer;
    private String verifiedAnswer;
    private String formattedAnswer;
    private String modelName;
    private int answerQualityScore;
    private String answerQualityLabel = "UNASSESSED";
    private String answerQualityNotes;
    private String answerQualityMethod = "HEURISTIC";
    private boolean answerQualityJudgeEnabled;
    private String answerQualityJudgeModelName;
    private int answerQualityConfidence;
    private String conversationHistory = "";
    private int memoryTurnCount;
    private int memoryChars;

    public AgentPipelineContext(ChatRequest request, User owner) {
        this.request = request;
        this.owner = owner;
        this.question = request.question().trim();
        this.searchQuery = this.question;
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

    public List<RetrievalChannelTrace> retrievalChannels() {
        return retrievalChannels;
    }

    public void retrievalChannels(List<RetrievalChannelTrace> retrievalChannels) {
        this.retrievalChannels = retrievalChannels == null ? List.of() : List.copyOf(retrievalChannels);
    }

    public List<RetrievalProcessorTrace> retrievalProcessors() {
        return retrievalProcessors;
    }

    public void retrievalProcessors(List<RetrievalProcessorTrace> retrievalProcessors) {
        this.retrievalProcessors = retrievalProcessors == null ? List.of() : List.copyOf(retrievalProcessors);
    }

    public List<QueryTermExpansion> queryExpansions() {
        return queryExpansions;
    }

    public void queryExpansions(List<QueryTermExpansion> queryExpansions) {
        this.queryExpansions = queryExpansions == null ? List.of() : List.copyOf(queryExpansions);
    }

    public String queryIntent() {
        return queryIntent;
    }

    public void queryIntent(String queryIntent) {
        this.queryIntent = queryIntent == null || queryIntent.isBlank() ? "GENERAL_QA" : queryIntent;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public void searchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null || searchQuery.isBlank() ? question : searchQuery.trim();
    }

    public boolean queryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public void queryRewriteEnabled(boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
    }

    public String rewrittenQuery() {
        return rewrittenQuery;
    }

    public void rewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery == null ? null : rewrittenQuery.trim();
    }

    public List<String> querySubQuestions() {
        return querySubQuestions;
    }

    public void querySubQuestions(List<String> querySubQuestions) {
        if (querySubQuestions == null) {
            this.querySubQuestions = List.of();
            return;
        }
        this.querySubQuestions = querySubQuestions.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .distinct()
            .limit(8)
            .toList();
    }

    public String queryRewriteModelName() {
        return queryRewriteModelName;
    }

    public void queryRewriteModelName(String queryRewriteModelName) {
        this.queryRewriteModelName = queryRewriteModelName == null ? null : queryRewriteModelName.trim();
    }

    public String planningQuestion() {
        return rewrittenQuery == null || rewrittenQuery.isBlank() ? question : rewrittenQuery;
    }

    public String planningSearchText() {
        String primary = planningQuestion();
        String joined = Stream.concat(Stream.of(primary), querySubQuestions.stream())
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .distinct()
            .reduce((left, right) -> left + " " + right)
            .orElse(primary);
        return joined.isBlank() ? question : joined;
    }

    public boolean comparisonRequested() {
        return comparisonRequested;
    }

    public void comparisonRequested(boolean comparisonRequested) {
        this.comparisonRequested = comparisonRequested;
    }

    public String answerStrategy() {
        return answerStrategy;
    }

    public void answerStrategy(String answerStrategy) {
        this.answerStrategy = answerStrategy == null || answerStrategy.isBlank() ? "EVIDENCE_GROUNDED_QA" : answerStrategy;
    }

    public String answerContract() {
        return answerContract;
    }

    public void answerContract(String answerContract) {
        this.answerContract = answerContract == null ? "" : answerContract.trim();
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

    public int answerQualityScore() {
        return answerQualityScore;
    }

    public void answerQualityScore(int answerQualityScore) {
        this.answerQualityScore = Math.max(0, Math.min(100, answerQualityScore));
    }

    public String answerQualityLabel() {
        return answerQualityLabel;
    }

    public void answerQualityLabel(String answerQualityLabel) {
        this.answerQualityLabel = answerQualityLabel == null || answerQualityLabel.isBlank() ? "UNASSESSED" : answerQualityLabel;
    }

    public String answerQualityNotes() {
        return answerQualityNotes;
    }

    public void answerQualityNotes(String answerQualityNotes) {
        this.answerQualityNotes = answerQualityNotes == null ? null : answerQualityNotes.trim();
    }

    public String answerQualityMethod() {
        return answerQualityMethod;
    }

    public void answerQualityMethod(String answerQualityMethod) {
        this.answerQualityMethod = answerQualityMethod == null || answerQualityMethod.isBlank() ? "HEURISTIC" : answerQualityMethod.trim();
    }

    public boolean answerQualityJudgeEnabled() {
        return answerQualityJudgeEnabled;
    }

    public void answerQualityJudgeEnabled(boolean answerQualityJudgeEnabled) {
        this.answerQualityJudgeEnabled = answerQualityJudgeEnabled;
    }

    public String answerQualityJudgeModelName() {
        return answerQualityJudgeModelName;
    }

    public void answerQualityJudgeModelName(String answerQualityJudgeModelName) {
        this.answerQualityJudgeModelName = answerQualityJudgeModelName == null ? null : answerQualityJudgeModelName.trim();
    }

    public int answerQualityConfidence() {
        return answerQualityConfidence;
    }

    public void answerQualityConfidence(int answerQualityConfidence) {
        this.answerQualityConfidence = Math.max(0, Math.min(100, answerQualityConfidence));
    }

    public String conversationHistory() {
        return conversationHistory;
    }

    public void conversationHistory(String conversationHistory) {
        this.conversationHistory = conversationHistory == null ? "" : conversationHistory.trim();
        this.memoryChars = this.conversationHistory.length();
    }

    public int memoryTurnCount() {
        return memoryTurnCount;
    }

    public void memoryTurnCount(int memoryTurnCount) {
        this.memoryTurnCount = Math.max(0, memoryTurnCount);
    }

    public int memoryChars() {
        return memoryChars;
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
