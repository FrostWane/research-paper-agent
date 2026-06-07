package com.frostwane.paperagent.agent.evaluation;

import com.frostwane.paperagent.agent.ChatRecord;
import com.frostwane.paperagent.agent.RagTrace;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "evaluation_cases")
public class EvaluationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private EvaluationDataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_owner_id")
    private User sourceOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_record_id")
    private ChatRecord chatRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rag_trace_id")
    private RagTrace ragTrace;

    @Column(nullable = false, length = 32)
    private String scope = "LIBRARY";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "expected_answer", nullable = false, columnDefinition = "TEXT")
    private String expectedAnswer;

    @Column(name = "expected_sources_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String expectedSourcesJson = "[]";

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, length = 32)
    private String difficulty = "MEDIUM";

    @Column(nullable = false)
    private Boolean enabled = true;

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

    public EvaluationDataset getDataset() {
        return dataset;
    }

    public void setDataset(EvaluationDataset dataset) {
        this.dataset = dataset;
    }

    public User getSourceOwner() {
        return sourceOwner;
    }

    public void setSourceOwner(User sourceOwner) {
        this.sourceOwner = sourceOwner;
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

    public RagTrace getRagTrace() {
        return ragTrace;
    }

    public void setRagTrace(RagTrace ragTrace) {
        this.ragTrace = ragTrace;
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

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getExpectedSourcesJson() {
        return expectedSourcesJson;
    }

    public void setExpectedSourcesJson(String expectedSourcesJson) {
        this.expectedSourcesJson = expectedSourcesJson;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
