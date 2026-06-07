package com.frostwane.paperagent.agent.evaluation;

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
@Table(name = "evaluation_case_results")
public class EvaluationCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private EvaluationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private EvaluationCase evaluationCase;

    @Column(nullable = false, length = 32)
    private String status = "REVIEW";

    @Column(nullable = false)
    private Integer score = 0;

    @Column(name = "answer_similarity", nullable = false)
    private Double answerSimilarity = 0.0;

    @Column(name = "source_coverage", nullable = false)
    private Double sourceCoverage = 0.0;

    @Column(name = "matched_sources", nullable = false)
    private Integer matchedSources = 0;

    @Column(name = "expected_sources", nullable = false)
    private Integer expectedSources = 0;

    @Column(name = "expected_answer", columnDefinition = "TEXT")
    private String expectedAnswer;

    @Column(name = "actual_answer", columnDefinition = "TEXT")
    private String actualAnswer;

    @Column(name = "expected_sources_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String expectedSourcesJson = "[]";

    @Column(name = "actual_sources_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actualSourcesJson = "[]";

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs = 0;

    @Column(name = "model_name", length = 160)
    private String modelName;

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

    public EvaluationRun getRun() {
        return run;
    }

    public void setRun(EvaluationRun run) {
        this.run = run;
    }

    public EvaluationCase getEvaluationCase() {
        return evaluationCase;
    }

    public void setEvaluationCase(EvaluationCase evaluationCase) {
        this.evaluationCase = evaluationCase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Double getAnswerSimilarity() {
        return answerSimilarity;
    }

    public void setAnswerSimilarity(Double answerSimilarity) {
        this.answerSimilarity = answerSimilarity;
    }

    public Double getSourceCoverage() {
        return sourceCoverage;
    }

    public void setSourceCoverage(Double sourceCoverage) {
        this.sourceCoverage = sourceCoverage;
    }

    public Integer getMatchedSources() {
        return matchedSources;
    }

    public void setMatchedSources(Integer matchedSources) {
        this.matchedSources = matchedSources;
    }

    public Integer getExpectedSources() {
        return expectedSources;
    }

    public void setExpectedSources(Integer expectedSources) {
        this.expectedSources = expectedSources;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getActualAnswer() {
        return actualAnswer;
    }

    public void setActualAnswer(String actualAnswer) {
        this.actualAnswer = actualAnswer;
    }

    public String getExpectedSourcesJson() {
        return expectedSourcesJson;
    }

    public void setExpectedSourcesJson(String expectedSourcesJson) {
        this.expectedSourcesJson = expectedSourcesJson;
    }

    public String getActualSourcesJson() {
        return actualSourcesJson;
    }

    public void setActualSourcesJson(String actualSourcesJson) {
        this.actualSourcesJson = actualSourcesJson;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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
