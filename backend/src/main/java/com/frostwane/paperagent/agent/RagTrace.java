package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rag_traces")
public class RagTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_record_id")
    private ChatRecord chatRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "pipeline_name", nullable = false, length = 120)
    private String pipelineName = "agent-pipeline-v1";

    @Column(name = "node_spans_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String nodeSpansJson = "[]";

    @Column(name = "retrieval_channels_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String retrievalChannelsJson = "[]";

    @Column(name = "retrieval_processors_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String retrievalProcessorsJson = "[]";

    @Column(name = "query_expansions_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String queryExpansionsJson = "[]";

    @Column(name = "tool_executions_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String toolExecutionsJson = "[]";

    @Column(name = "guidance_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String guidanceJson = "{}";

    @Column(name = "query_intent", nullable = false, length = 64)
    private String queryIntent = "GENERAL_QA";

    @Column(name = "search_query", columnDefinition = "TEXT")
    private String searchQuery;

    @Column(name = "query_rewrite_enabled", nullable = false)
    private Boolean queryRewriteEnabled = false;

    @Column(name = "rewritten_query", columnDefinition = "TEXT")
    private String rewrittenQuery;

    @Column(name = "query_sub_questions_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String querySubQuestionsJson = "[]";

    @Column(name = "query_rewrite_model_name", length = 120)
    private String queryRewriteModelName;

    @Column(name = "comparison_requested", nullable = false)
    private Boolean comparisonRequested = false;

    @Column(name = "answer_strategy", nullable = false, length = 64)
    private String answerStrategy = "EVIDENCE_GROUNDED_QA";

    @Column(name = "answer_contract", columnDefinition = "TEXT")
    private String answerContract;

    @Column(name = "source_count", nullable = false)
    private Integer sourceCount = 0;

    @Column(name = "memory_turn_count", nullable = false)
    private Integer memoryTurnCount = 0;

    @Column(name = "memory_chars", nullable = false)
    private Integer memoryChars = 0;

    @Column(name = "memory_summary_used", nullable = false)
    private Boolean memorySummaryUsed = false;

    @Column(name = "memory_summary_turn_count", nullable = false)
    private Integer memorySummaryTurnCount = 0;

    @Column(name = "memory_summary_chars", nullable = false)
    private Integer memorySummaryChars = 0;

    @Column(name = "memory_summary_method", nullable = false, length = 32)
    private String memorySummaryMethod = "NONE";

    @Column(name = "memory_summary_model_name", length = 120)
    private String memorySummaryModelName;

    @Column(name = "retrieval_ms", nullable = false)
    private Integer retrievalMs = 0;

    @Column(name = "generation_ms", nullable = false)
    private Integer generationMs = 0;

    @Column(name = "verification_ms", nullable = false)
    private Integer verificationMs = 0;

    @Column(name = "formatting_ms", nullable = false)
    private Integer formattingMs = 0;

    @Column(name = "answer_quality_score", nullable = false)
    private Integer answerQualityScore = 0;

    @Column(name = "answer_quality_label", nullable = false, length = 32)
    private String answerQualityLabel = "UNASSESSED";

    @Column(name = "answer_quality_notes", columnDefinition = "TEXT")
    private String answerQualityNotes;

    @Column(name = "answer_quality_method", nullable = false, length = 32)
    private String answerQualityMethod = "HEURISTIC";

    @Column(name = "answer_quality_judge_enabled", nullable = false)
    private Boolean answerQualityJudgeEnabled = false;

    @Column(name = "answer_quality_judge_model_name", length = 120)
    private String answerQualityJudgeModelName;

    @Column(name = "answer_quality_confidence", nullable = false)
    private Integer answerQualityConfidence = 0;

    @Column(name = "evaluation_ms", nullable = false)
    private Integer evaluationMs = 0;

    @Column(name = "total_ms", nullable = false)
    private Integer totalMs = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Paper getPaper() {
        return paper;
    }

    public void setPaper(Paper paper) {
        this.paper = paper;
    }

    public ChatRecord getChatRecord() {
        return chatRecord;
    }

    public void setChatRecord(ChatRecord chatRecord) {
        this.chatRecord = chatRecord;
    }

    public ChatSession getSession() {
        return session;
    }

    public void setSession(ChatSession session) {
        this.session = session;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public String getNodeSpansJson() {
        return nodeSpansJson;
    }

    public void setNodeSpansJson(String nodeSpansJson) {
        this.nodeSpansJson = nodeSpansJson;
    }

    public String getRetrievalChannelsJson() {
        return retrievalChannelsJson;
    }

    public void setRetrievalChannelsJson(String retrievalChannelsJson) {
        this.retrievalChannelsJson = retrievalChannelsJson;
    }

    public String getRetrievalProcessorsJson() {
        return retrievalProcessorsJson;
    }

    public void setRetrievalProcessorsJson(String retrievalProcessorsJson) {
        this.retrievalProcessorsJson = retrievalProcessorsJson;
    }

    public String getQueryExpansionsJson() {
        return queryExpansionsJson;
    }

    public void setQueryExpansionsJson(String queryExpansionsJson) {
        this.queryExpansionsJson = queryExpansionsJson;
    }

    public String getToolExecutionsJson() {
        return toolExecutionsJson;
    }

    public void setToolExecutionsJson(String toolExecutionsJson) {
        this.toolExecutionsJson = toolExecutionsJson;
    }

    public String getGuidanceJson() {
        return guidanceJson;
    }

    public void setGuidanceJson(String guidanceJson) {
        this.guidanceJson = guidanceJson;
    }

    public String getQueryIntent() {
        return queryIntent;
    }

    public void setQueryIntent(String queryIntent) {
        this.queryIntent = queryIntent;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public Boolean getQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public void setQueryRewriteEnabled(Boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public String getQuerySubQuestionsJson() {
        return querySubQuestionsJson;
    }

    public void setQuerySubQuestionsJson(String querySubQuestionsJson) {
        this.querySubQuestionsJson = querySubQuestionsJson;
    }

    public String getQueryRewriteModelName() {
        return queryRewriteModelName;
    }

    public void setQueryRewriteModelName(String queryRewriteModelName) {
        this.queryRewriteModelName = queryRewriteModelName;
    }

    public Boolean getComparisonRequested() {
        return comparisonRequested;
    }

    public void setComparisonRequested(Boolean comparisonRequested) {
        this.comparisonRequested = comparisonRequested;
    }

    public String getAnswerStrategy() {
        return answerStrategy;
    }

    public void setAnswerStrategy(String answerStrategy) {
        this.answerStrategy = answerStrategy;
    }

    public String getAnswerContract() {
        return answerContract;
    }

    public void setAnswerContract(String answerContract) {
        this.answerContract = answerContract;
    }

    public Integer getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(Integer sourceCount) {
        this.sourceCount = sourceCount;
    }

    public Integer getMemoryTurnCount() {
        return memoryTurnCount;
    }

    public void setMemoryTurnCount(Integer memoryTurnCount) {
        this.memoryTurnCount = memoryTurnCount;
    }

    public Integer getMemoryChars() {
        return memoryChars;
    }

    public void setMemoryChars(Integer memoryChars) {
        this.memoryChars = memoryChars;
    }

    public Boolean getMemorySummaryUsed() {
        return memorySummaryUsed;
    }

    public void setMemorySummaryUsed(Boolean memorySummaryUsed) {
        this.memorySummaryUsed = memorySummaryUsed;
    }

    public Integer getMemorySummaryTurnCount() {
        return memorySummaryTurnCount;
    }

    public void setMemorySummaryTurnCount(Integer memorySummaryTurnCount) {
        this.memorySummaryTurnCount = memorySummaryTurnCount;
    }

    public Integer getMemorySummaryChars() {
        return memorySummaryChars;
    }

    public void setMemorySummaryChars(Integer memorySummaryChars) {
        this.memorySummaryChars = memorySummaryChars;
    }

    public String getMemorySummaryMethod() {
        return memorySummaryMethod;
    }

    public void setMemorySummaryMethod(String memorySummaryMethod) {
        this.memorySummaryMethod = memorySummaryMethod;
    }

    public String getMemorySummaryModelName() {
        return memorySummaryModelName;
    }

    public void setMemorySummaryModelName(String memorySummaryModelName) {
        this.memorySummaryModelName = memorySummaryModelName;
    }

    public Integer getRetrievalMs() {
        return retrievalMs;
    }

    public void setRetrievalMs(Integer retrievalMs) {
        this.retrievalMs = retrievalMs;
    }

    public Integer getGenerationMs() {
        return generationMs;
    }

    public void setGenerationMs(Integer generationMs) {
        this.generationMs = generationMs;
    }

    public Integer getVerificationMs() {
        return verificationMs;
    }

    public void setVerificationMs(Integer verificationMs) {
        this.verificationMs = verificationMs;
    }

    public Integer getFormattingMs() {
        return formattingMs;
    }

    public void setFormattingMs(Integer formattingMs) {
        this.formattingMs = formattingMs;
    }

    public Integer getAnswerQualityScore() {
        return answerQualityScore;
    }

    public void setAnswerQualityScore(Integer answerQualityScore) {
        this.answerQualityScore = answerQualityScore;
    }

    public String getAnswerQualityLabel() {
        return answerQualityLabel;
    }

    public void setAnswerQualityLabel(String answerQualityLabel) {
        this.answerQualityLabel = answerQualityLabel;
    }

    public String getAnswerQualityNotes() {
        return answerQualityNotes;
    }

    public void setAnswerQualityNotes(String answerQualityNotes) {
        this.answerQualityNotes = answerQualityNotes;
    }

    public String getAnswerQualityMethod() {
        return answerQualityMethod;
    }

    public void setAnswerQualityMethod(String answerQualityMethod) {
        this.answerQualityMethod = answerQualityMethod;
    }

    public Boolean getAnswerQualityJudgeEnabled() {
        return answerQualityJudgeEnabled;
    }

    public void setAnswerQualityJudgeEnabled(Boolean answerQualityJudgeEnabled) {
        this.answerQualityJudgeEnabled = answerQualityJudgeEnabled;
    }

    public String getAnswerQualityJudgeModelName() {
        return answerQualityJudgeModelName;
    }

    public void setAnswerQualityJudgeModelName(String answerQualityJudgeModelName) {
        this.answerQualityJudgeModelName = answerQualityJudgeModelName;
    }

    public Integer getAnswerQualityConfidence() {
        return answerQualityConfidence;
    }

    public void setAnswerQualityConfidence(Integer answerQualityConfidence) {
        this.answerQualityConfidence = answerQualityConfidence;
    }

    public Integer getEvaluationMs() {
        return evaluationMs;
    }

    public void setEvaluationMs(Integer evaluationMs) {
        this.evaluationMs = evaluationMs;
    }

    public Integer getTotalMs() {
        return totalMs;
    }

    public void setTotalMs(Integer totalMs) {
        this.totalMs = totalMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
