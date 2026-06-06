package com.frostwane.paperagent.agent.settings;

import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsResponse;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final RagSettingsRepository repository;

    public RagSettingsService(RagSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RagSettingsSnapshot snapshot() {
        return snapshot(load());
    }

    @Transactional(readOnly = true)
    public RagSettingsResponse get(User currentUser) {
        requireAdmin(currentUser);
        return response(load());
    }

    @Transactional
    public RagSettingsResponse update(RagSettingsRequest request, User currentUser) {
        requireAdmin(currentUser);
        RagSettings settings = load();
        settings.setCandidateLimit(clamp(request.candidateLimit(), 1, 50, 10));
        settings.setResultLimit(clamp(request.resultLimit(), 1, 20, 5));
        settings.setSourceExcerptChars(clamp(request.sourceExcerptChars(), 120, 1200, 520));
        settings.setVectorWeight(clamp(request.vectorWeight(), 0.0d, 3.0d, 1.0d));
        settings.setKeywordWeight(clamp(request.keywordWeight(), 0.0d, 3.0d, 0.78d));
        settings.touch();
        return response(repository.save(settings));
    }

    private RagSettings load() {
        return repository.findById(SETTINGS_ID).orElseGet(this::defaults);
    }

    private RagSettings defaults() {
        RagSettings settings = new RagSettings();
        settings.setId(SETTINGS_ID);
        return settings;
    }

    private RagSettingsSnapshot snapshot(RagSettings settings) {
        return new RagSettingsSnapshot(
            clamp(settings.getCandidateLimit(), 1, 50, 10),
            clamp(settings.getResultLimit(), 1, 20, 5),
            clamp(settings.getSourceExcerptChars(), 120, 1200, 520),
            clamp(settings.getVectorWeight(), 0.0d, 3.0d, 1.0d),
            clamp(settings.getKeywordWeight(), 0.0d, 3.0d, 0.78d)
        );
    }

    private RagSettingsResponse response(RagSettings settings) {
        RagSettingsSnapshot snapshot = snapshot(settings);
        return new RagSettingsResponse(
            snapshot.candidateLimit(),
            snapshot.resultLimit(),
            snapshot.sourceExcerptChars(),
            snapshot.vectorWeight(),
            snapshot.keywordWeight(),
            settings.getUpdatedAt()
        );
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int candidate = value == null ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }

    private double clamp(Double value, double min, double max, double fallback) {
        double candidate = value == null || !Double.isFinite(value) ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }
}
