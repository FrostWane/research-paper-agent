package com.frostwane.paperagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatFeedbackRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionCreateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionUpdateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.limit.AgentRateLimitPermit;
import com.frostwane.paperagent.agent.limit.AgentRateLimiterService;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.agent.pipeline.AgentNodeType;
import com.frostwane.paperagent.agent.pipeline.AgentPipeline;
import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperService;
import com.frostwane.paperagent.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AgentOrchestratorService {

    private final AgentPipeline agentPipeline;
    private final PaperService paperService;
    private final ChatRecordRepository chatRecordRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ConversationSummaryService conversationSummaryService;
    private final AgentRateLimiterService agentRateLimiterService;
    private final RagTraceService ragTraceService;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
        AgentPipeline agentPipeline,
        PaperService paperService,
        ChatRecordRepository chatRecordRepository,
        ChatSessionRepository chatSessionRepository,
        ConversationSummaryService conversationSummaryService,
        AgentRateLimiterService agentRateLimiterService,
        RagTraceService ragTraceService,
        ObjectMapper objectMapper
    ) {
        this.agentPipeline = agentPipeline;
        this.paperService = paperService;
        this.chatRecordRepository = chatRecordRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.conversationSummaryService = conversationSummaryService;
        this.agentRateLimiterService = agentRateLimiterService;
        this.ragTraceService = ragTraceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, User owner) {
        Instant started = Instant.now();
        AgentPipelineContext context = new AgentPipelineContext(request, owner);

        try (AgentRateLimitPermit ignored = agentRateLimiterService.acquire(owner)) {
            ChatSession session = resolveSession(request, owner);
            context.chatSession(session);
            agentPipeline.execute(context);
            int latencyMs = elapsedMs(started);

            ChatRecord record = new ChatRecord();
            record.setOwner(owner);
            record.setSession(session);
            record.setPaper(context.paper());
            record.setQuestion(context.question());
            record.setAnswer(context.formattedAnswer());
            record.setSourcesJson(toJson(context.sources()));
            record.setModelName(context.modelName());
            record.setLatencyMs(latencyMs);
            ChatRecord saved = chatRecordRepository.save(record);
            ChatSession savedSession = touchSession(session, context.question(), saved.getCreatedAt());

            ragTraceService.recordSuccess(
                owner,
                savedSession,
                context.paper(),
                saved,
                context.scope(),
                context.question(),
                context.modelName(),
                agentPipeline.name(),
                toJson(context.nodeSpans()),
                toJson(context.retrievalChannels()),
                toJson(context.retrievalProcessors()),
                toJson(context.queryExpansions()),
                toJson(context.toolExecutions()),
                toJson(context.guidanceTrace()),
                context.queryIntent(),
                context.searchQuery(),
                context.queryRewriteEnabled(),
                context.rewrittenQuery(),
                toJson(context.querySubQuestions()),
                context.queryRewriteModelName(),
                context.comparisonRequested(),
                context.answerStrategy(),
                context.answerContract(),
                context.sourceCount(),
                context.contextTokenBudget(),
                context.contextEstimatedTokens(),
                context.contextTruncated(),
                context.memoryTurnCount(),
                context.memoryChars(),
                context.memorySummaryUsed(),
                context.memorySummaryTurnCount(),
                context.memorySummaryChars(),
                context.memorySummaryMethod(),
                context.memorySummaryModelName(),
                context.timingMs(AgentNodeType.RETRIEVAL),
                context.timingMs(AgentNodeType.GENERATION),
                context.timingMs(AgentNodeType.VERIFICATION),
                context.timingMs(AgentNodeType.FORMATTING),
                context.timingMs(AgentNodeType.EVALUATION),
                context.answerQualityScore(),
                context.answerQualityLabel(),
                context.answerQualityNotes(),
                context.answerQualityMethod(),
                context.answerQualityJudgeEnabled(),
                context.answerQualityJudgeModelName(),
                context.answerQualityConfidence(),
                latencyMs
            );
            refreshConversationSummary(owner, savedSession);

            return new ChatResponse(
                context.formattedAnswer(),
                context.sources(),
                saved.getId(),
                savedSession.getId(),
                savedSession.getTitle(),
                context.modelName(),
                latencyMs
            );
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

    @Transactional
    public ChatResponse evaluate(ChatRequest request, User owner) {
        Instant started = Instant.now();
        AgentPipelineContext context = new AgentPipelineContext(request, owner);
        try (AgentRateLimitPermit ignored = agentRateLimiterService.acquire(owner)) {
            agentPipeline.execute(context);
            int latencyMs = elapsedMs(started);
            return new ChatResponse(
                context.formattedAnswer(),
                context.sources(),
                null,
                null,
                null,
                context.modelName(),
                latencyMs
            );
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

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(Long paperId, User owner) {
        if (paperId == null) {
            return chatSessionRepository.findByOwnerIdAndPaperIsNullAndArchivedFalseOrderByUpdatedAtDesc(owner.getId())
                .stream()
                .map(this::toSessionResponse)
                .toList();
        }
        paperService.requireOwnedPaper(paperId, owner.getId());
        return chatSessionRepository.findByOwnerIdAndPaperIdAndArchivedFalseOrderByUpdatedAtDesc(owner.getId(), paperId)
            .stream()
            .map(this::toSessionResponse)
            .toList();
    }

    @Transactional
    public ChatSessionResponse createSession(ChatSessionCreateRequest request, User owner) {
        Paper paper = request.paperId() == null ? null : paperService.requireOwnedPaper(request.paperId(), owner.getId());
        ChatSession session = new ChatSession();
        session.setOwner(owner);
        session.setPaper(paper);
        session.setScope(paper == null ? "LIBRARY" : "PAPER");
        session.setTitle(defaultText(compact(request.title(), 160), "新对话"));
        session.setArchived(false);
        session.setMessageCount(0);
        return toSessionResponse(chatSessionRepository.save(session));
    }

    @Transactional
    public ChatSessionResponse updateSession(Long sessionId, ChatSessionUpdateRequest request, User owner) {
        ChatSession session = requireSession(sessionId, owner);
        if (request.title() != null) {
            String title = compact(request.title(), 160);
            if (title == null) {
                throw new BusinessException("会话标题不能为空");
            }
            session.setTitle(title);
        }
        if (request.archived() != null) {
            session.setArchived(request.archived());
        }
        return toSessionResponse(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatRecordResponse> listSessionChats(Long sessionId, User owner) {
        requireSession(sessionId, owner);
        return chatRecordRepository.findByOwnerIdAndSessionIdOrderByCreatedAtAsc(owner.getId(), sessionId)
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
            record.getSession() == null ? null : record.getSession().getId(),
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

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
            session.getId(),
            session.getPaper() == null ? null : session.getPaper().getId(),
            defaultText(session.getScope(), session.getPaper() == null ? "LIBRARY" : "PAPER"),
            session.getTitle(),
            Boolean.TRUE.equals(session.getArchived()),
            session.getMessageCount() == null ? 0 : session.getMessageCount(),
            session.getLastMessageAt(),
            session.getCreatedAt(),
            session.getUpdatedAt()
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

    private ChatSession resolveSession(ChatRequest request, User owner) {
        if (request.sessionId() != null) {
            ChatSession session = requireSession(request.sessionId(), owner);
            if (Boolean.TRUE.equals(session.getArchived())) {
                throw new BusinessException("会话已归档，不能继续追加问答");
            }
            Long sessionPaperId = session.getPaper() == null ? null : session.getPaper().getId();
            if (!Objects.equals(sessionPaperId, request.paperId())) {
                throw new BusinessException("会话范围与本次问答范围不一致");
            }
            return session;
        }
        Paper paper = request.paperId() == null ? null : paperService.requireOwnedPaper(request.paperId(), owner.getId());
        return latestSession(owner, paper)
            .stream()
            .findFirst()
            .orElseGet(() -> createTransientSession(owner, paper, titleFromQuestion(request.question())));
    }

    private List<ChatSession> latestSession(User owner, Paper paper) {
        PageRequest page = PageRequest.of(0, 1);
        if (paper == null) {
            return chatSessionRepository.findByOwnerIdAndPaperIsNullAndArchivedFalseOrderByUpdatedAtDesc(owner.getId(), page);
        }
        return chatSessionRepository.findByOwnerIdAndPaperIdAndArchivedFalseOrderByUpdatedAtDesc(owner.getId(), paper.getId(), page);
    }

    private ChatSession createTransientSession(User owner, Paper paper, String title) {
        ChatSession session = new ChatSession();
        session.setOwner(owner);
        session.setPaper(paper);
        session.setScope(paper == null ? "LIBRARY" : "PAPER");
        session.setTitle(defaultText(title, "新对话"));
        session.setArchived(false);
        session.setMessageCount(0);
        return chatSessionRepository.save(session);
    }

    private ChatSession requireSession(Long sessionId, User owner) {
        return chatSessionRepository.findByIdAndOwnerId(sessionId, owner.getId())
            .orElseThrow(() -> new BusinessException("会话不存在"));
    }

    private ChatSession touchSession(ChatSession session, String question, OffsetDateTime answeredAt) {
        session.setMessageCount(Math.max(0, session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        session.setLastMessageAt(answeredAt == null ? OffsetDateTime.now() : answeredAt);
        if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
            session.setTitle(titleFromQuestion(question));
        }
        return chatSessionRepository.save(session);
    }

    private String titleFromQuestion(String question) {
        String title = compact(question, 42);
        return defaultText(title, "新对话");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void refreshConversationSummary(User owner, ChatSession session) {
        try {
            conversationSummaryService.refreshAfterTurn(owner, session);
        } catch (RuntimeException ignored) {
            // Long-term memory compression is best effort; the answer and trace are already persisted.
        }
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
                context.chatSession(),
                context.paper(),
                context.scope(),
                context.question(),
                context.modelName(),
                agentPipeline.name(),
                toJson(context.nodeSpans()),
                toJson(context.retrievalChannels()),
                toJson(context.retrievalProcessors()),
                toJson(context.queryExpansions()),
                toJson(context.toolExecutions()),
                toJson(context.guidanceTrace()),
                context.queryIntent(),
                context.searchQuery(),
                context.queryRewriteEnabled(),
                context.rewrittenQuery(),
                toJson(context.querySubQuestions()),
                context.queryRewriteModelName(),
                context.comparisonRequested(),
                context.answerStrategy(),
                context.answerContract(),
                context.sourceCount(),
                context.contextTokenBudget(),
                context.contextEstimatedTokens(),
                context.contextTruncated(),
                context.memoryTurnCount(),
                context.memoryChars(),
                context.memorySummaryUsed(),
                context.memorySummaryTurnCount(),
                context.memorySummaryChars(),
                context.memorySummaryMethod(),
                context.memorySummaryModelName(),
                context.timingMs(AgentNodeType.RETRIEVAL),
                context.timingMs(AgentNodeType.GENERATION),
                context.timingMs(AgentNodeType.VERIFICATION),
                context.timingMs(AgentNodeType.FORMATTING),
                context.timingMs(AgentNodeType.EVALUATION),
                context.answerQualityScore(),
                context.answerQualityLabel(),
                context.answerQualityNotes(),
                context.answerQualityMethod(),
                context.answerQualityJudgeEnabled(),
                context.answerQualityJudgeModelName(),
                context.answerQualityConfidence(),
                totalMs,
                exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        } catch (RuntimeException ignored) {
            // Trace writes are diagnostic only and should not mask the original chat failure.
        }
    }
}
