package com.frostwane.paperagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.agent.AnswerAgent.GeneratedAnswer;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperService;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AgentOrchestratorService {

    private final PaperService paperService;
    private final RetrieverAgent retrieverAgent;
    private final AnswerAgent answerAgent;
    private final CitationVerifierAgent citationVerifierAgent;
    private final FormatterAgent formatterAgent;
    private final ChatRecordRepository chatRecordRepository;
    private final RagTraceService ragTraceService;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
        PaperService paperService,
        RetrieverAgent retrieverAgent,
        AnswerAgent answerAgent,
        CitationVerifierAgent citationVerifierAgent,
        FormatterAgent formatterAgent,
        ChatRecordRepository chatRecordRepository,
        RagTraceService ragTraceService,
        ObjectMapper objectMapper
    ) {
        this.paperService = paperService;
        this.retrieverAgent = retrieverAgent;
        this.answerAgent = answerAgent;
        this.citationVerifierAgent = citationVerifierAgent;
        this.formatterAgent = formatterAgent;
        this.chatRecordRepository = chatRecordRepository;
        this.ragTraceService = ragTraceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, User owner) {
        Instant started = Instant.now();
        String question = request.question().trim();
        String scope = request.paperId() == null ? "LIBRARY" : "PAPER";
        Paper paper = null;
        List<SourceResponse> sources = List.of();
        String modelName = null;
        int retrievalMs = 0;
        int generationMs = 0;
        int verificationMs = 0;
        int formattingMs = 0;

        try {
            paper = request.paperId() == null ? null : paperService.requireOwnedPaper(request.paperId(), owner.getId());

            Instant segmentStarted = Instant.now();
            sources = paper == null
                ? retrieverAgent.retrieveLibrary(owner, question, request.useRag())
                : retrieverAgent.retrieve(paper, question, request.useRag());
            retrievalMs = elapsedMs(segmentStarted);

            segmentStarted = Instant.now();
            GeneratedAnswer generated = answerAgent.answer(paper, question, sources);
            generationMs = elapsedMs(segmentStarted);
            modelName = generated.modelName();

            segmentStarted = Instant.now();
            String verified = citationVerifierAgent.verify(generated.content(), sources);
            verificationMs = elapsedMs(segmentStarted);

            segmentStarted = Instant.now();
            String formatted = formatterAgent.format(verified);
            formattingMs = elapsedMs(segmentStarted);
            int latencyMs = elapsedMs(started);

            ChatRecord record = new ChatRecord();
            record.setOwner(owner);
            record.setPaper(paper);
            record.setQuestion(question);
            record.setAnswer(formatted);
            record.setSourcesJson(toJson(sources));
            record.setModelName(modelName);
            record.setLatencyMs(latencyMs);
            ChatRecord saved = chatRecordRepository.save(record);

            ragTraceService.recordSuccess(
                owner,
                paper,
                saved,
                scope,
                question,
                modelName,
                sources.size(),
                retrievalMs,
                generationMs,
                verificationMs,
                formattingMs,
                latencyMs
            );

            return new ChatResponse(formatted, sources, saved.getId(), modelName, latencyMs);
        } catch (RuntimeException ex) {
            recordFailureTrace(
                owner,
                paper,
                scope,
                question,
                modelName,
                sources.size(),
                retrievalMs,
                generationMs,
                verificationMs,
                formattingMs,
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

    private ChatRecordResponse toResponse(ChatRecord record) {
        return new ChatRecordResponse(
            record.getId(),
            record.getPaper() == null ? null : record.getPaper().getId(),
            record.getQuestion(),
            record.getAnswer(),
            fromJson(record.getSourcesJson()),
            record.getModelName(),
            record.getLatencyMs(),
            record.getCreatedAt()
        );
    }

    private String toJson(List<SourceResponse> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
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

    private void recordFailureTrace(
        User owner,
        Paper paper,
        String scope,
        String question,
        String modelName,
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int totalMs,
        RuntimeException exception
    ) {
        try {
            ragTraceService.recordFailure(
                owner,
                paper,
                scope,
                question,
                modelName,
                sourceCount,
                retrievalMs,
                generationMs,
                verificationMs,
                formattingMs,
                totalMs,
                exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        } catch (RuntimeException ignored) {
            // Trace writes are diagnostic only and should not mask the original chat failure.
        }
    }
}
