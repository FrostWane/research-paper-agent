package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentPipelineNodeSettingService {

    private static final Set<AgentNodeType> DISABLE_ALLOWED_TYPES = EnumSet.of(
        AgentNodeType.MEMORY,
        AgentNodeType.QUERY_REWRITE,
        AgentNodeType.TOOL_EXECUTION,
        AgentNodeType.INTENT_GUIDANCE,
        AgentNodeType.VERIFICATION,
        AgentNodeType.EVALUATION
    );

    private final AgentPipelineNodeSettingRepository repository;

    public AgentPipelineNodeSettingService(AgentPipelineNodeSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean enabled(AgentNode node) {
        if (!canDisable(node)) {
            return true;
        }
        return repository.findById(normalize(node.name()))
            .map(setting -> Boolean.TRUE.equals(setting.getEnabled()))
            .orElse(true);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> enabledByNodeName() {
        return repository.findAll().stream()
            .collect(Collectors.toMap(
                AgentPipelineNodeSetting::getNodeName,
                setting -> Boolean.TRUE.equals(setting.getEnabled())
            ));
    }

    public boolean canDisable(AgentNode node) {
        return node != null && DISABLE_ALLOWED_TYPES.contains(node.type());
    }

    @Transactional
    public boolean updateEnabled(AgentNode node, boolean enabled) {
        if (!canDisable(node) && !enabled) {
            throw new BusinessException("该 Agent Pipeline 节点属于主链路，不能停用");
        }
        String key = normalize(node.name());
        AgentPipelineNodeSetting setting = repository.findById(key).orElseGet(() -> {
            AgentPipelineNodeSetting created = new AgentPipelineNodeSetting();
            created.setNodeName(key);
            return created;
        });
        setting.setEnabled(enabled || !canDisable(node));
        return Boolean.TRUE.equals(repository.save(setting).getEnabled());
    }

    private String normalize(String nodeName) {
        return nodeName == null ? "" : nodeName.trim();
    }
}
