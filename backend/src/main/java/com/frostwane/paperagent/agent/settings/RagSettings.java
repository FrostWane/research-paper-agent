package com.frostwane.paperagent.agent.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rag_settings")
public class RagSettings {

    @Id
    private Long id = 1L;

    @Column(name = "candidate_limit", nullable = false)
    private Integer candidateLimit = 10;

    @Column(name = "result_limit", nullable = false)
    private Integer resultLimit = 5;

    @Column(name = "source_excerpt_chars", nullable = false)
    private Integer sourceExcerptChars = 520;

    @Column(name = "vector_weight", nullable = false)
    private Double vectorWeight = 1.0d;

    @Column(name = "keyword_weight", nullable = false)
    private Double keywordWeight = 0.78d;

    @Column(name = "memory_history_turns", nullable = false)
    private Integer memoryHistoryTurns = 4;

    @Column(name = "memory_max_chars", nullable = false)
    private Integer memoryMaxChars = 2400;

    @Column(name = "query_rewrite_enabled", nullable = false)
    private Boolean queryRewriteEnabled = true;

    @Column(name = "query_rewrite_max_sub_questions", nullable = false)
    private Integer queryRewriteMaxSubQuestions = 3;

    @Column(name = "answer_quality_judge_enabled", nullable = false)
    private Boolean answerQualityJudgeEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() {
        touch();
    }

    public void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(Integer candidateLimit) {
        this.candidateLimit = candidateLimit;
    }

    public Integer getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(Integer resultLimit) {
        this.resultLimit = resultLimit;
    }

    public Integer getSourceExcerptChars() {
        return sourceExcerptChars;
    }

    public void setSourceExcerptChars(Integer sourceExcerptChars) {
        this.sourceExcerptChars = sourceExcerptChars;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public Integer getMemoryHistoryTurns() {
        return memoryHistoryTurns;
    }

    public void setMemoryHistoryTurns(Integer memoryHistoryTurns) {
        this.memoryHistoryTurns = memoryHistoryTurns;
    }

    public Integer getMemoryMaxChars() {
        return memoryMaxChars;
    }

    public void setMemoryMaxChars(Integer memoryMaxChars) {
        this.memoryMaxChars = memoryMaxChars;
    }

    public Boolean getQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public void setQueryRewriteEnabled(Boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
    }

    public Integer getQueryRewriteMaxSubQuestions() {
        return queryRewriteMaxSubQuestions;
    }

    public void setQueryRewriteMaxSubQuestions(Integer queryRewriteMaxSubQuestions) {
        this.queryRewriteMaxSubQuestions = queryRewriteMaxSubQuestions;
    }

    public Boolean getAnswerQualityJudgeEnabled() {
        return answerQualityJudgeEnabled;
    }

    public void setAnswerQualityJudgeEnabled(Boolean answerQualityJudgeEnabled) {
        this.answerQualityJudgeEnabled = answerQualityJudgeEnabled;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
