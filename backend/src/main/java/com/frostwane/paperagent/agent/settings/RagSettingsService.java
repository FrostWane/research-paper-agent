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
        settings.setMemoryHistoryTurns(clamp(request.memoryHistoryTurns(), 0, 12, 4));
        settings.setMemoryMaxChars(clamp(request.memoryMaxChars(), 0, 8000, 2400));
        settings.setMemorySummaryEnabled(request.memorySummaryEnabled() == null || request.memorySummaryEnabled());
        settings.setMemorySummaryStartTurns(clamp(request.memorySummaryStartTurns(), 2, 50, 6));
        settings.setMemorySummaryMaxChars(clamp(request.memorySummaryMaxChars(), 300, 6000, 1800));
        settings.setQueryRewriteEnabled(request.queryRewriteEnabled() == null || request.queryRewriteEnabled());
        settings.setQueryRewriteMaxSubQuestions(clamp(request.queryRewriteMaxSubQuestions(), 1, 6, 3));
        settings.setAnswerQualityJudgeEnabled(request.answerQualityJudgeEnabled() == null || request.answerQualityJudgeEnabled());
        settings.setRerankModelEnabled(Boolean.TRUE.equals(request.rerankModelEnabled()));
        settings.setRerankModelMaxCandidates(clamp(request.rerankModelMaxCandidates(), 2, 20, 8));
        settings.setChatRateLimitEnabled(request.chatRateLimitEnabled() == null || request.chatRateLimitEnabled());
        settings.setChatRateLimitGlobalConcurrency(clamp(request.chatRateLimitGlobalConcurrency(), 1, 100, 12));
        settings.setChatRateLimitUserConcurrency(clamp(request.chatRateLimitUserConcurrency(), 1, 20, 2));
        settings.setChatRateLimitUserPerMinute(clamp(request.chatRateLimitUserPerMinute(), 1, 600, 20));
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
            clamp(settings.getKeywordWeight(), 0.0d, 3.0d, 0.78d),
            clamp(settings.getMemoryHistoryTurns(), 0, 12, 4),
            clamp(settings.getMemoryMaxChars(), 0, 8000, 2400),
            Boolean.TRUE.equals(settings.getMemorySummaryEnabled()),
            clamp(settings.getMemorySummaryStartTurns(), 2, 50, 6),
            clamp(settings.getMemorySummaryMaxChars(), 300, 6000, 1800),
            Boolean.TRUE.equals(settings.getQueryRewriteEnabled()),
            clamp(settings.getQueryRewriteMaxSubQuestions(), 1, 6, 3),
            Boolean.TRUE.equals(settings.getAnswerQualityJudgeEnabled()),
            Boolean.TRUE.equals(settings.getRerankModelEnabled()),
            clamp(settings.getRerankModelMaxCandidates(), 2, 20, 8),
            Boolean.TRUE.equals(settings.getChatRateLimitEnabled()),
            clamp(settings.getChatRateLimitGlobalConcurrency(), 1, 100, 12),
            clamp(settings.getChatRateLimitUserConcurrency(), 1, 20, 2),
            clamp(settings.getChatRateLimitUserPerMinute(), 1, 600, 20)
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
            snapshot.memoryHistoryTurns(),
            snapshot.memoryMaxChars(),
            snapshot.memorySummaryEnabled(),
            snapshot.memorySummaryStartTurns(),
            snapshot.memorySummaryMaxChars(),
            snapshot.queryRewriteEnabled(),
            snapshot.queryRewriteMaxSubQuestions(),
            snapshot.answerQualityJudgeEnabled(),
            snapshot.rerankModelEnabled(),
            snapshot.rerankModelMaxCandidates(),
            snapshot.chatRateLimitEnabled(),
            snapshot.chatRateLimitGlobalConcurrency(),
            snapshot.chatRateLimitUserConcurrency(),
            snapshot.chatRateLimitUserPerMinute(),
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
