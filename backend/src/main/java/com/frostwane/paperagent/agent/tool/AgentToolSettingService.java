package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentToolSettingService {

    private final AgentToolSettingRepository repository;

    public AgentToolSettingService(AgentToolSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean enabled(String toolName) {
        return repository.findById(normalize(toolName))
            .map(setting -> Boolean.TRUE.equals(setting.getEnabled()))
            .orElse(true);
    }

    @Transactional(readOnly = true)
    public UserRole minimumRole(String toolName) {
        return repository.findById(normalize(toolName))
            .map(AgentToolSetting::getMinimumRole)
            .orElse(UserRole.USER);
    }

    @Transactional(readOnly = true)
    public boolean available(String toolName, UserRole currentRole) {
        ToolSettingSnapshot setting = setting(toolName);
        return setting.enabled() && roleAllows(currentRole, setting.minimumRole());
    }

    @Transactional(readOnly = true)
    public Map<String, ToolSettingSnapshot> settingsByToolName() {
        return repository.findAll().stream()
            .collect(Collectors.toMap(
                AgentToolSetting::getToolName,
                setting -> new ToolSettingSnapshot(
                    Boolean.TRUE.equals(setting.getEnabled()),
                    setting.getMinimumRole() == null ? UserRole.USER : setting.getMinimumRole()
                )
            ));
    }

    @Transactional
    public boolean updateEnabled(String toolName, boolean enabled) {
        AgentToolSetting setting = editableSetting(toolName);
        setting.setEnabled(enabled);
        return Boolean.TRUE.equals(repository.save(setting).getEnabled());
    }

    @Transactional
    public UserRole updateMinimumRole(String toolName, UserRole minimumRole) {
        AgentToolSetting setting = editableSetting(toolName);
        setting.setMinimumRole(minimumRole);
        UserRole savedRole = repository.save(setting).getMinimumRole();
        return savedRole == null ? UserRole.USER : savedRole;
    }

    private ToolSettingSnapshot setting(String toolName) {
        return repository.findById(normalize(toolName))
            .map(setting -> new ToolSettingSnapshot(
                Boolean.TRUE.equals(setting.getEnabled()),
                setting.getMinimumRole() == null ? UserRole.USER : setting.getMinimumRole()
            ))
            .orElse(ToolSettingSnapshot.defaults());
    }

    private AgentToolSetting editableSetting(String toolName) {
        String key = normalize(toolName);
        return repository.findById(key).orElseGet(() -> {
            AgentToolSetting created = new AgentToolSetting();
            created.setToolName(key);
            return created;
        });
    }

    private boolean roleAllows(UserRole currentRole, UserRole minimumRole) {
        return roleRank(currentRole) >= roleRank(minimumRole);
    }

    private int roleRank(UserRole role) {
        return role == UserRole.ADMIN ? 2 : 1;
    }

    private String normalize(String toolName) {
        return toolName == null ? "" : toolName.trim();
    }

    public record ToolSettingSnapshot(boolean enabled, UserRole minimumRole) {
        static ToolSettingSnapshot defaults() {
            return new ToolSettingSnapshot(true, UserRole.USER);
        }
    }
}
