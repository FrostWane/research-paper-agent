package com.frostwane.paperagent.agent.tool;

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
    public Map<String, Boolean> enabledByToolName() {
        return repository.findAll().stream()
            .collect(Collectors.toMap(
                AgentToolSetting::getToolName,
                setting -> Boolean.TRUE.equals(setting.getEnabled())
            ));
    }

    @Transactional
    public boolean updateEnabled(String toolName, boolean enabled) {
        String key = normalize(toolName);
        AgentToolSetting setting = repository.findById(key).orElseGet(() -> {
            AgentToolSetting created = new AgentToolSetting();
            created.setToolName(key);
            return created;
        });
        setting.setEnabled(enabled);
        return Boolean.TRUE.equals(repository.save(setting).getEnabled());
    }

    private String normalize(String toolName) {
        return toolName == null ? "" : toolName.trim();
    }
}
