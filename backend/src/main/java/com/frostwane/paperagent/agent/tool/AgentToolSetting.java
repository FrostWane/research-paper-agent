package com.frostwane.paperagent.agent.tool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.frostwane.paperagent.user.UserRole;

import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_tool_settings")
public class AgentToolSetting {

    @Id
    @Column(name = "tool_name", length = 120)
    private String toolName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_role", nullable = false, length = 32)
    private UserRole minimumRole = UserRole.USER;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UserRole getMinimumRole() {
        return minimumRole;
    }

    public void setMinimumRole(UserRole minimumRole) {
        this.minimumRole = minimumRole == null ? UserRole.USER : minimumRole;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
