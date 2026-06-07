package com.frostwane.paperagent.agent.intent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "intent_routes")
public class IntentRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_code", nullable = false, length = 64)
    private String intentCode;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "search_hint", length = 500)
    private String searchHint;

    @Column(name = "answer_strategy", nullable = false, length = 64)
    private String answerStrategy;

    @Column(name = "answer_contract", columnDefinition = "TEXT")
    private String answerContract;

    @Column(name = "bound_tool_name", length = 120)
    private String boundToolName;

    @Column(name = "comparison_enabled", nullable = false)
    private Boolean comparisonEnabled = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIntentCode() {
        return intentCode;
    }

    public void setIntentCode(String intentCode) {
        this.intentCode = intentCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSearchHint() {
        return searchHint;
    }

    public void setSearchHint(String searchHint) {
        this.searchHint = searchHint;
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

    public String getBoundToolName() {
        return boundToolName;
    }

    public void setBoundToolName(String boundToolName) {
        this.boundToolName = boundToolName;
    }

    public Boolean getComparisonEnabled() {
        return comparisonEnabled;
    }

    public void setComparisonEnabled(Boolean comparisonEnabled) {
        this.comparisonEnabled = comparisonEnabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
