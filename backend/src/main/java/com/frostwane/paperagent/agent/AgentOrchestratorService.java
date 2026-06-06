package com.frostwane.paperagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatFeedbackRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.agent.pipeline.AgentNodeType;
import com.frostwane.paperagent.agent.pipeline.AgentPipeline;
import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.paper.PaperService;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AgentOrchestratorService {

    private final AgentPipeline agentPipeline;
    private final PaperService paperService;
    private final ChatRecordRepository chatRecordRepository;
    private final RagTraceService ragTraceService;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
        AgentPipeline agentPipeline,
        PaperService paperService,
        ChatRecordRepository chatRecordRepository,
        RagTraceService ragTraceService,
        ObjectMapper objectMapper
    ) {
        this.agentPipeline = agentPipeline;
        this.paperService = paperService;
        this.chatRecordRepository = chatRecordRepository;
        this.ragTraceService = ragTraceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, User owner) {
        Instant started = Instant.now();
        AgentPipelineContext context = new AgentPipelineContext(request, owner);

        try {
            agentPipeline.execute(context);
            int latencyMs = elapsedMs(started);

            ChatRecord record = new ChatRecord();
            record.setOwner(owner);
            record.setPaper(context.paper());
            record.setQuestion(context.question());
            record.setAnswer(context.formattedAnswer());
            record.setSourcesJson(toJson(context.sources()));
            record.setModelName(context.modelName());
            record.setLatencyMs(latencyMs);
            ChatRecord saved = chatRecordRepository.save(record);

            ragTraceService.recordSuccess(
                owner,
                context.paper(),
                saved,
                context.scope(),
                context.question(),
                context.modelName(),
                agentPipeline.name(),
                toJson(context.nodeSpans()),
                toJson(context.retrievalChannels()),
                toJson(context.retrievalProcessors()),
                context.queryIntent(),
                context.searchQuery(),
                context.comparisonRequested(),
                context.answerStrategy(),
                context.answerContract(),
                context.sourceCount(),
                context.timingMs(AgentNodeType.RETRIEVAL),
                context.timingMs(AgentNodeType.GENERATION),
                context.timingMs(AgentNodeType.VERIFICATION),
                context.timingMs(AgentNodeType.FORMATTING),
                latencyMs
            );

            return new ChatResponse(context.formattedAnswer(), context.sources(), saved.getId(), context.modelName(), latencyMs);
        } catch (RuntimeException ex) {
            recordFailureTrace(
                owner,
                context,
                elapsedMs(started),
                ex
            );
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ChatRecordResponse> listChats(Long paperId, User owner) {
        paperService.requireOwnedPaper(paperId, owner.getId());
        return chatRecordRepository.findByOwnerIdAndPaperIdOrderByCreatedAtAsc(owner.getId(), paperId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatRecordResponse> listLibraryChats(User owner) {
        return chatRecordRepository.findByOwnerIdAndPaperIsNullOrderByCreatedAtAsc(owner.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ChatRecordResponse feedback(Long chatId, ChatFeedbackRequest request, User owner) {
        ChatRecord record = chatRecordRepository.findByIdAndOwnerId(chatId, owner.getId())
            .orElseThrow(() -> new BusinessException("问答记录不存在"));
        Integer score = request.score();
        if (score != null && score != 1 && score != -1) {
            throw new BusinessException("反馈只能是有用或无用");
        }
        record.setFeedbackScore(score);
        record.setFeedbackComment(score == null ? null : compact(request.comment(), 500));
        record.setFeedbackAt(score == null ? null : OffsetDateTime.now());
        return toResponse(chatRecordRepository.save(record));
    }

    private ChatRecordResponse toResponse(ChatRecord record) {
        return new ChatRecordResponse(
            record.getId(),
            record.getPaper() == null ? null : record.getPaper().getId(),
            record.getQuestion(),
            record.getAnswer(),
            fromJson(record.getSourcesJson()),
            record.getModelName(),
            record.getLatencyMs(),
            record.getFeedbackScore(),
            record.getFeedbackComment(),
            record.getFeedbackAt(),
            record.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<SourceResponse> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private void recordFailureTrace(
        User owner,
        AgentPipelineContext context,
        int totalMs,
        RuntimeException exception
    ) {
        try {
            ragTraceService.recordFailure(
                owner,
                context.paper(),
                context.scope(),
                context.question(),
                context.modelName(),
                agentPipeline.name(),
                toJson(context.nodeSpans()),
                toJson(context.retrievalChannels()),
                toJson(context.retrievalProcessors()),
                context.queryIntent(),
                context.searchQuery(),
                context.comparisonRequested(),
                context.answerStrategy(),
                context.answerContract(),
                context.sourceCount(),
                context.timingMs(AgentNodeType.RETRIEVAL),
                context.timingMs(AgentNodeType.GENERATION),
                context.timingMs(AgentNodeType.VERIFICATION),
                context.timingMs(AgentNodeType.FORMATTING),
                totalMs,
                exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        } catch (RuntimeException ignored) {
            // Trace writes are diagnostic only and should not mask the original chat failure.
        }
    }
}
