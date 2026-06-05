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

    @Column(name = "query_intent", nullable = false, length = 64)
    private String queryIntent = "GENERAL_QA";

    @Column(name = "search_query", columnDefinition = "TEXT")
    private String searchQuery;

    @Column(name = "comparison_requested", nullable = false)
    private Boolean comparisonRequested = false;

    @Column(name = "answer_strategy", nullable = false, length = 64)
    private String answerStrategy = "EVIDENCE_GROUNDED_QA";

    @Column(name = "answer_contract", columnDefinition = "TEXT")
    private String answerContract;

    @Column(name = "source_count", nullable = false)
    private Integer sourceCount = 0;

    @Column(name = "retrieval_ms", nullable = false)
    private Integer retrievalMs = 0;

    @Column(name = "generation_ms", nullable = false)
    private Integer generationMs = 0;

    @Column(name = "verification_ms", nullable = false)
    private Integer verificationMs = 0;

    @Column(name = "formatting_ms", nullable = false)
    private Integer formattingMs = 0;

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
