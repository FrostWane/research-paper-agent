package com.frostwane.paperagent.agent.intent;

import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class IntentRouteService {

    private final IntentRouteRepository repository;

    public IntentRouteService(IntentRouteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public IntentRouteMatch match(String normalizedQuestion) {
        String normalized = normalize(normalizedQuestion);
        return repository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
            .map(route -> new ScoredRoute(route, score(route, normalized)))
            .filter(item -> item.score() > 0)
            .max(Comparator.comparingInt(ScoredRoute::score)
                .thenComparing(item -> -safeInt(item.route().getSortOrder(), 100)))
            .map(item -> match(item.route()))
            .orElse(IntentRouteMatch.general());
    }

    @Transactional(readOnly = true)
    public IntentRouteMatch resolve(String intentCode) {
        if (intentCode == null || intentCode.isBlank() || "GENERAL_QA".equalsIgnoreCase(intentCode)) {
            return IntentRouteMatch.general();
        }
        return repository.findByIntentCodeIgnoreCase(intentCode)
            .filter(route -> Boolean.TRUE.equals(route.getEnabled()))
            .map(this::match)
            .orElse(IntentRouteMatch.general());
    }

    @Transactional(readOnly = true)
    public List<IntentRouteResponse> list(User currentUser) {
        requireAdmin(currentUser);
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(this::response)
            .toList();
    }

    @Transactional
    public IntentRouteResponse create(IntentRouteRequest request, User currentUser) {
        requireAdmin(currentUser);
        String code = normalizeCode(request.intentCode());
        repository.findByIntentCodeIgnoreCase(code).ifPresent(existing -> {
            throw new BusinessException("意图标识已存在");
        });
        IntentRoute route = new IntentRoute();
        apply(route, request, code);
        return response(repository.save(route));
    }

    @Transactional
    public IntentRouteResponse update(Long id, IntentRouteRequest request, User currentUser) {
        requireAdmin(currentUser);
        IntentRoute route = repository.findById(id).orElseThrow(() -> new BusinessException("意图路由不存在"));
        String code = normalizeCode(request.intentCode());
        repository.findByIntentCodeIgnoreCase(code)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException("意图标识已存在");
            });
        apply(route, request, code);
        return response(repository.save(route));
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!repository.existsById(id)) {
            throw new BusinessException("意图路由不存在");
        }
        repository.deleteById(id);
    }

    private void apply(IntentRoute route, IntentRouteRequest request, String code) {
        String label = compact(request.label(), 120);
        String keywords = compact(request.keywords(), 2000);
        String answerStrategy = normalizeCode(request.answerStrategy());
        if (label == null || keywords == null || answerStrategy == null) {
            throw new BusinessException("意图标识、名称、关键词和回答策略不能为空");
        }
        route.setIntentCode(code);
        route.setLabel(label);
        route.setDescription(compact(request.description(), 500));
        route.setKeywords(keywords);
        route.setSearchHint(compact(request.searchHint(), 500));
        route.setAnswerStrategy(answerStrategy);
        route.setAnswerContract(compact(request.answerContract(), 2000));
        route.setComparisonEnabled(Boolean.TRUE.equals(request.comparisonEnabled()));
        route.setEnabled(request.enabled() == null || request.enabled());
        route.setSortOrder(clamp(request.sortOrder(), 0, 1000, 100));
    }

    private IntentRouteMatch match(IntentRoute route) {
        return new IntentRouteMatch(
            route.getIntentCode(),
            route.getLabel(),
            route.getSearchHint(),
            route.getAnswerStrategy(),
            route.getAnswerContract(),
            Boolean.TRUE.equals(route.getComparisonEnabled())
        );
    }

    private IntentRouteResponse response(IntentRoute route) {
        return new IntentRouteResponse(
            route.getId(),
            route.getIntentCode(),
            route.getLabel(),
            route.getDescription(),
            route.getKeywords(),
            route.getSearchHint(),
            route.getAnswerStrategy(),
            route.getAnswerContract(),
            Boolean.TRUE.equals(route.getComparisonEnabled()),
            Boolean.TRUE.equals(route.getEnabled()),
            safeInt(route.getSortOrder(), 100),
            route.getCreatedAt(),
            route.getUpdatedAt()
        );
    }

    private int score(IntentRoute route, String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return 0;
        }
        return Arrays.stream(route.getKeywords().split("[,;；，、|\\n]+"))
            .map(this::normalize)
            .filter(token -> token.length() >= 2)
            .filter(normalizedQuestion::contains)
            .mapToInt(token -> Math.min(token.length(), 12))
            .sum();
    }

    private String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{Punct}，。！？；：、（）【】《》]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeCode(String value) {
        String normalized = compact(value, 64);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
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

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record ScoredRoute(IntentRoute route, int score) {
    }
}
