package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.ChatRecord;
import com.frostwane.paperagent.agent.ChatRecordRepository;
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
    private final RagSettingsService ragSettingsService;

    public ConversationMemoryNode(ChatRecordRepository chatRecordRepository, RagSettingsService ragSettingsService) {
        this.chatRecordRepository = chatRecordRepository;
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
            context.memoryTurnCount(0);
            context.conversationHistory("");
            return;
        }
        List<ChatRecord> recent = loadRecent(context, settings.memoryHistoryTurns());
        if (recent.isEmpty()) {
            context.memoryTurnCount(0);
            context.conversationHistory("");
            return;
        }
        Collections.reverse(recent);
        MemoryText memory = render(recent, settings.memoryMaxChars());
        context.memoryTurnCount(memory.turnCount());
        context.conversationHistory(memory.text());
    }

    private List<ChatRecord> loadRecent(AgentPipelineContext context, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        if (context.libraryScope()) {
            return new ArrayList<>(chatRecordRepository.findByOwnerIdAndPaperIsNullOrderByCreatedAtDesc(context.owner().getId(), page));
        }
        return new ArrayList<>(chatRecordRepository.findByOwnerIdAndPaperIdOrderByCreatedAtDesc(context.owner().getId(), context.paper().getId(), page));
    }

    private MemoryText render(List<ChatRecord> records, int maxChars) {
        StringBuilder builder = new StringBuilder();
        int used = 0;
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
            int projected = builder.length() + (builder.isEmpty() ? 0 : 2) + turn.length();
            if (projected > maxChars && count > 0) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(projected > maxChars ? compact(turn, maxChars - used) : turn);
            used = builder.length();
            count++;
            if (used >= maxChars) {
                break;
            }
        }
        return new MemoryText(builder.toString(), count);
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

    private record MemoryText(String text, int turnCount) {
    }
}
