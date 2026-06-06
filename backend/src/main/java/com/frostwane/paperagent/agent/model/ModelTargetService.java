package com.frostwane.paperagent.agent.model;

import com.frostwane.paperagent.admin.dto.AdminDtos.ModelTargetRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.ModelTargetResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.config.PaperAgentProperties;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class ModelTargetService {

    private static final String ENV_PROVIDER = "ENV";
    private static final String OPENAI_COMPATIBLE_PROVIDER = "OPENAI_COMPATIBLE";

    private final ModelTargetRepository repository;
    private final PaperAgentProperties properties;

    public ModelTargetService(ModelTargetRepository repository, PaperAgentProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ModelTargetResponse> list(User currentUser) {
        requireAdmin(currentUser);
        return repository.findAllByOrderByPriorityAscIdAsc().stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RoutingTarget> routingTargets() {
        return routingTargets(ModelTaskType.ANSWER_GENERATION);
    }

    @Transactional(readOnly = true)
    public List<RoutingTarget> routingTargets(ModelTaskType taskType) {
        ModelTaskType requestedTask = taskType == null ? ModelTaskType.ANSWER_GENERATION : taskType;
        List<ModelTarget> enabledTargets = repository.findAllByEnabledTrueOrderByPriorityAscIdAsc();
        if (enabledTargets.isEmpty()) {
            return List.of(envDefaultTarget());
        }
        Stream<ModelTarget> taskTargets = enabledTargets.stream()
            .filter(target -> requestedTask.code().equals(targetTaskType(target)));
        Stream<ModelTarget> generalTargets = requestedTask == ModelTaskType.GENERAL
            ? Stream.empty()
            : enabledTargets.stream().filter(target -> ModelTaskType.GENERAL.code().equals(targetTaskType(target)));
        return Stream.concat(taskTargets, generalTargets)
            .map(this::routingTarget)
            .toList();
    }

    @Transactional
    public ModelTargetResponse create(ModelTargetRequest request, User currentUser) {
        requireAdmin(currentUser);
        String code = normalizeCode(request.code());
        repository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new BusinessException("模型目标标识已存在");
        });
        ModelTarget target = new ModelTarget();
        apply(target, request, code, false);
        return response(repository.save(target));
    }

    @Transactional
    public ModelTargetResponse update(Long id, ModelTargetRequest request, User currentUser) {
        requireAdmin(currentUser);
        ModelTarget target = repository.findById(id).orElseThrow(() -> new BusinessException("模型目标不存在"));
        String code = normalizeCode(request.code());
        repository.findByCodeIgnoreCase(code)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException("模型目标标识已存在");
            });
        apply(target, request, code, true);
        return response(repository.save(target));
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        requireAdmin(currentUser);
        ModelTarget target = repository.findById(id).orElseThrow(() -> new BusinessException("模型目标不存在"));
        if ("ENV_DEFAULT".equalsIgnoreCase(target.getCode())) {
            throw new BusinessException("不能删除环境默认模型目标，可停用它");
        }
        repository.delete(target);
    }

    private void apply(ModelTarget target, ModelTargetRequest request, String code, boolean preserveBlankApiKey) {
        String provider = normalizeProvider(request.provider());
        String modelName = compact(request.modelName(), 160);
        if (code == null || provider == null || modelName == null) {
            throw new BusinessException("模型目标标识、供应商和模型名不能为空");
        }
        String baseUrl = compact(request.baseUrl(), 500);
        if (OPENAI_COMPATIBLE_PROVIDER.equals(provider) && baseUrl == null) {
            throw new BusinessException("OpenAI-compatible 模型目标必须填写 Base URL");
        }
        target.setCode(code);
        target.setProvider(provider);
        target.setTaskType(normalizeTaskType(request.taskType(), target.getTaskType()));
        target.setModelName(modelName);
        target.setDescription(compact(request.description(), 500));
        target.setBaseUrl(ENV_PROVIDER.equals(provider) ? null : baseUrl);
        String apiKey = compact(request.apiKey(), 2000);
        if (ENV_PROVIDER.equals(provider)) {
            target.setApiKey(null);
        } else if (apiKey != null || !preserveBlankApiKey) {
            target.setApiKey(apiKey);
        }
        target.setEnabled(request.enabled() == null || request.enabled());
        target.setPriority(clamp(request.priority(), 0, 1000, 100));
        target.setTimeoutSeconds(clamp(request.timeoutSeconds(), 1, 120, 45));
    }

    private RoutingTarget routingTarget(ModelTarget target) {
        if (ENV_PROVIDER.equalsIgnoreCase(target.getProvider())) {
            return envTarget(target);
        }
        return new RoutingTarget(
            target.getCode(),
            target.getProvider(),
            targetTaskType(target),
            target.getModelName(),
            target.getCode() + ":" + target.getModelName(),
            target.getBaseUrl(),
            target.getApiKey(),
            clamp(target.getTimeoutSeconds(), 1, 120, 45),
            false
        );
    }

    private RoutingTarget envTarget(ModelTarget target) {
        String provider = defaultText(properties.getAi().getProvider(), "fallback");
        String modelName = defaultText(properties.getAi().getChatModel(), "unknown-model");
        return new RoutingTarget(
            target.getCode(),
            provider,
            targetTaskType(target),
            modelName,
            "fallback".equalsIgnoreCase(provider) ? "fallback-agent" : provider + ":" + modelName,
            null,
            null,
            clamp(target.getTimeoutSeconds(), 1, 120, 45),
            true
        );
    }

    private RoutingTarget envDefaultTarget() {
        String provider = defaultText(properties.getAi().getProvider(), "fallback");
        String modelName = defaultText(properties.getAi().getChatModel(), "unknown-model");
        return new RoutingTarget("ENV_DEFAULT", provider, ModelTaskType.GENERAL.code(), modelName, "fallback".equalsIgnoreCase(provider) ? "fallback-agent" : provider + ":" + modelName, null, null, 45, true);
    }

    private ModelTargetResponse response(ModelTarget target) {
        return new ModelTargetResponse(
            target.getId(),
            target.getCode(),
            target.getProvider(),
            targetTaskType(target),
            target.getModelName(),
            target.getDescription(),
            target.getBaseUrl(),
            target.getApiKey() != null && !target.getApiKey().isBlank(),
            Boolean.TRUE.equals(target.getEnabled()),
            target.getPriority() == null ? 100 : target.getPriority(),
            target.getTimeoutSeconds() == null ? 45 : target.getTimeoutSeconds(),
            target.getCreatedAt(),
            target.getUpdatedAt()
        );
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private String normalizeCode(String value) {
        String normalized = compact(value, 64);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeProvider(String value) {
        String normalized = compact(value, 64);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        if (!ENV_PROVIDER.equals(normalized) && !OPENAI_COMPATIBLE_PROVIDER.equals(normalized)) {
            throw new BusinessException("模型供应商仅支持 ENV 或 OPENAI_COMPATIBLE");
        }
        return normalized;
    }

    private String normalizeTaskType(String value, String fallback) {
        try {
            ModelTaskType fallbackTask = ModelTaskType.fromCode(fallback, ModelTaskType.GENERAL);
            return ModelTaskType.fromCode(value, fallbackTask).code();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("任务类型仅支持 GENERAL、ANSWER_GENERATION 或 QUERY_REWRITE");
        }
    }

    private String targetTaskType(ModelTarget target) {
        return ModelTaskType.fromCode(target.getTaskType(), ModelTaskType.GENERAL).code();
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int candidate = value == null ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record RoutingTarget(
        String code,
        String provider,
        String taskType,
        String modelName,
        String targetName,
        String baseUrl,
        String apiKey,
        int timeoutSeconds,
        boolean environmentTarget
    ) {
    }
}
