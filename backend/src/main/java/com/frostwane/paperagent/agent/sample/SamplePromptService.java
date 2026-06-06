package com.frostwane.paperagent.agent.sample;

import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class SamplePromptService {

    private final SamplePromptRepository repository;

    public SamplePromptService(SamplePromptRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SamplePromptResponse> listEnabled(String scope) {
        return repository.findByEnabledTrueAndScopeOrderBySortOrderAscUpdatedAtDesc(normalizeScope(scope))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SamplePromptResponse> listAll(User currentUser) {
        requireAdmin(currentUser);
        return repository.findAllByOrderBySortOrderAscUpdatedAtDesc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public SamplePromptResponse create(SamplePromptRequest request, User currentUser) {
        requireAdmin(currentUser);
        SamplePrompt prompt = new SamplePrompt();
        apply(prompt, request);
        return toResponse(repository.save(prompt));
    }

    @Transactional
    public SamplePromptResponse update(Long id, SamplePromptRequest request, User currentUser) {
        requireAdmin(currentUser);
        SamplePrompt prompt = repository.findById(id).orElseThrow(() -> new BusinessException("示例问题不存在"));
        apply(prompt, request);
        return toResponse(repository.save(prompt));
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!repository.existsById(id)) {
            throw new BusinessException("示例问题不存在");
        }
        repository.deleteById(id);
    }

    private void apply(SamplePrompt prompt, SamplePromptRequest request) {
        String title = compact(request.title(), 120);
        String text = compact(request.prompt(), 2000);
        if (title == null || text == null) {
            throw new BusinessException("示例问题标题和内容不能为空");
        }
        prompt.setScope(normalizeScope(request.scope()));
        prompt.setTitle(title);
        prompt.setPrompt(text);
        prompt.setDescription(compact(request.description(), 255));
        prompt.setSortOrder(request.sortOrder() == null ? 100 : Math.max(0, request.sortOrder()));
        prompt.setEnabled(request.enabled() == null || request.enabled());
    }

    private SamplePromptResponse toResponse(SamplePrompt prompt) {
        return new SamplePromptResponse(
            prompt.getId(),
            prompt.getScope(),
            prompt.getTitle(),
            prompt.getPrompt(),
            prompt.getDescription(),
            prompt.getSortOrder(),
            Boolean.TRUE.equals(prompt.getEnabled()),
            prompt.getCreatedAt(),
            prompt.getUpdatedAt()
        );
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private String normalizeScope(String value) {
        String scope = value == null || value.isBlank() ? "LIBRARY" : value.trim().toUpperCase(Locale.ROOT);
        if ("PAPER".equals(scope) || "LIBRARY".equals(scope)) {
            return scope;
        }
        throw new BusinessException("示例问题范围只能是 PAPER 或 LIBRARY");
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
