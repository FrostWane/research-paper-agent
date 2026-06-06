package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.ChatRecord;
import com.frostwane.paperagent.agent.ChatRecordRepository;
import com.frostwane.paperagent.agent.ChatSessionSummary;
import com.frostwane.paperagent.agent.ChatSessionSummaryRepository;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ConversationMemoryNode implements AgentNode {

    private final ChatRecordRepository chatRecordRepository;
    private final ChatSessionSummaryRepository summaryRepository;
    private final RagSettingsService ragSettingsService;

    public ConversationMemoryNode(
        ChatRecordRepository chatRecordRepository,
        ChatSessionSummaryRepository summaryRepository,
        RagSettingsService ragSettingsService
    ) {
        this.chatRecordRepository = chatRecordRepository;
        this.summaryRepository = summaryRepository;
        this.ragSettingsService = ragSettingsService;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.MEMORY;
    }

    @Override
    public String name() {
        return "conversation-memory";
    }

    @Override
    public int order() {
        return 12;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        if (settings.memoryHistoryTurns() <= 0 || settings.memoryMaxChars() <= 0) {
            clearMemory(context);
            return;
        }
        ChatSessionSummary summary = loadSummary(context, settings);
        List<ChatRecord> recent = loadRecent(context, settings.memoryHistoryTurns());
        if (recent.isEmpty() && summary == null) {
            clearMemory(context);
            return;
        }
        Collections.reverse(recent);
        MemoryText memory = render(summary, recent, settings.memoryMaxChars());
        context.memoryTurnCount(memory.turnCount());
        context.memorySummaryUsed(memory.summaryUsed());
        context.memorySummaryTurnCount(memory.summaryTurnCount());
        context.memorySummaryChars(memory.summaryChars());
        context.memorySummaryMethod(memory.summaryMethod());
        context.memorySummaryModelName(memory.summaryModelName());
        context.conversationHistory(memory.text());
    }

    private void clearMemory(AgentPipelineContext context) {
        context.memoryTurnCount(0);
        context.memorySummaryUsed(false);
        context.memorySummaryTurnCount(0);
        context.memorySummaryChars(0);
        context.memorySummaryMethod("NONE");
        context.memorySummaryModelName(null);
        context.conversationHistory("");
    }

    private ChatSessionSummary loadSummary(AgentPipelineContext context, RagSettingsSnapshot settings) {
        if (!settings.memorySummaryEnabled() || context.chatSession() == null || context.chatSession().getId() == null) {
            return null;
        }
        return summaryRepository.findFirstBySessionIdOrderByIdDesc(context.chatSession().getId())
            .filter(summary -> summary.getContent() != null && !summary.getContent().isBlank())
            .orElse(null);
    }

    private List<ChatRecord> loadRecent(AgentPipelineContext context, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        if (context.chatSession() != null) {
            return new ArrayList<>(chatRecordRepository.findByOwnerIdAndSessionIdOrderByCreatedAtDesc(
                context.owner().getId(),
                context.chatSession().getId(),
                page
            ));
        }
        if (context.libraryScope()) {
            return new ArrayList<>(chatRecordRepository.findByOwnerIdAndPaperIsNullOrderByCreatedAtDesc(context.owner().getId(), page));
        }
        return new ArrayList<>(chatRecordRepository.findByOwnerIdAndPaperIdOrderByCreatedAtDesc(context.owner().getId(), context.paper().getId(), page));
    }

    private MemoryText render(ChatSessionSummary summary, List<ChatRecord> records, int maxChars) {
        StringBuilder builder = new StringBuilder();
        boolean summaryUsed = summary != null && summary.getContent() != null && !summary.getContent().isBlank();
        int summaryChars = summaryUsed ? summary.getContent().length() : 0;
        int summaryTurnCount = summaryUsed && summary.getTurnCount() != null ? summary.getTurnCount() : 0;
        String summaryMethod = summaryUsed ? summary.getMethod() : "NONE";
        String summaryModelName = summaryUsed ? summary.getModelName() : null;
        if (summaryUsed) {
            String summarySection = "长期会话摘要：\n" + compact(summary.getContent(), Math.max(320, maxChars / 2));
            builder.append(compact(summarySection, maxChars));
            if (builder.length() >= maxChars) {
                return new MemoryText(
                    builder.toString(),
                    0,
                    true,
                    summaryTurnCount,
                    summaryChars,
                    summaryMethod,
                    summaryModelName
                );
            }
        }
        int remainingChars = Math.max(0, maxChars - builder.length());
        if (!builder.isEmpty() && !records.isEmpty()) {
            String header = "\n\n近期对话：\n";
            if (remainingChars <= header.length()) {
                return new MemoryText(
                    compact(builder.toString(), maxChars),
                    0,
                    summaryUsed,
                    summaryTurnCount,
                    summaryChars,
                    summaryMethod,
                    summaryModelName
                );
            }
            builder.append(header);
            remainingChars -= header.length();
        }
        int count = 0;
        for (ChatRecord record : records) {
            String turn = """
                历史第 %d 轮用户：%s
                历史第 %d 轮助手：%s
                """.formatted(
                count + 1,
                compact(record.getQuestion(), 360),
                count + 1,
                compact(record.getAnswer(), 900)
            ).trim();
            String separator = count == 0 ? "" : "\n\n";
            int turnBudget = remainingChars - separator.length();
            if (turnBudget <= 0) {
                break;
            }
            if (turn.length() > turnBudget && count > 0) {
                break;
            }
            String renderedTurn = turn.length() > turnBudget ? compact(turn, turnBudget) : turn;
            if (renderedTurn.isBlank()) {
                break;
            }
            builder.append(separator);
            builder.append(renderedTurn);
            remainingChars -= separator.length() + renderedTurn.length();
            count++;
            if (remainingChars <= 0) {
                break;
            }
        }
        return new MemoryText(
            compact(builder.toString(), maxChars),
            count,
            summaryUsed,
            summaryTurnCount,
            summaryChars,
            summaryMethod,
            summaryModelName
        );
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "无";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (maxLength <= 0) {
            return "";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private record MemoryText(
        String text,
        int turnCount,
        boolean summaryUsed,
        int summaryTurnCount,
        int summaryChars,
        String summaryMethod,
        String summaryModelName
    ) {
    }
}
