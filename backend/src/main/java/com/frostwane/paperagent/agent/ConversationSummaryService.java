package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.ConversationSummaryAgent.SummaryResult;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationSummaryService {

    private final ChatRecordRepository chatRecordRepository;
    private final ChatSessionSummaryRepository summaryRepository;
    private final ConversationSummaryAgent summaryAgent;
    private final RagSettingsService ragSettingsService;

    public ConversationSummaryService(
        ChatRecordRepository chatRecordRepository,
        ChatSessionSummaryRepository summaryRepository,
        ConversationSummaryAgent summaryAgent,
        RagSettingsService ragSettingsService
    ) {
        this.chatRecordRepository = chatRecordRepository;
        this.summaryRepository = summaryRepository;
        this.summaryAgent = summaryAgent;
        this.ragSettingsService = ragSettingsService;
    }

    @Transactional
    public void refreshAfterTurn(User owner, ChatSession session) {
        if (owner == null || session == null || session.getId() == null) {
            return;
        }
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        if (!settings.memorySummaryEnabled() || settings.memorySummaryMaxChars() <= 0) {
            return;
        }
        int messageCount = session.getMessageCount() == null ? 0 : session.getMessageCount();
        if (messageCount < settings.memorySummaryStartTurns()) {
            return;
        }
        ChatSessionSummary latest = summaryRepository.findFirstBySessionIdOrderByIdDesc(session.getId()).orElse(null);
        Long lastSummarizedId = latest == null || latest.getLastChatRecord() == null ? 0L : latest.getLastChatRecord().getId();
        List<ChatRecord> unsummarized = lastSummarizedId <= 0
            ? chatRecordRepository.findByOwnerIdAndSessionIdOrderByCreatedAtAsc(owner.getId(), session.getId())
            : chatRecordRepository.findByOwnerIdAndSessionIdAndIdGreaterThanOrderByCreatedAtAsc(owner.getId(), session.getId(), lastSummarizedId);
        int keepRecent = Math.max(1, settings.memoryHistoryTurns());
        if (unsummarized.size() <= keepRecent) {
            return;
        }
        List<ChatRecord> recordsToSummarize = unsummarized.subList(0, unsummarized.size() - keepRecent);
        if (recordsToSummarize.isEmpty()) {
            return;
        }
        String existingSummary = latest == null ? "" : latest.getContent();
        SummaryResult result = summaryAgent.summarize(existingSummary, recordsToSummarize, settings.memorySummaryMaxChars());
        if (result.content() == null || result.content().isBlank()) {
            return;
        }
        ChatSessionSummary summary = new ChatSessionSummary();
        summary.setOwner(owner);
        summary.setSession(session);
        summary.setLastChatRecord(recordsToSummarize.get(recordsToSummarize.size() - 1));
        summary.setTurnCount((latest == null || latest.getTurnCount() == null ? 0 : latest.getTurnCount()) + recordsToSummarize.size());
        summary.setContent(result.content());
        summary.setMethod(defaultText(result.method(), "HEURISTIC"));
        summary.setModelName(result.modelName());
        summaryRepository.save(summary);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
