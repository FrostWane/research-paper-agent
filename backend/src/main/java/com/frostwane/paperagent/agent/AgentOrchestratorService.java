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
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
        PaperService paperService,
        RetrieverAgent retrieverAgent,
        AnswerAgent answerAgent,
        CitationVerifierAgent citationVerifierAgent,
        FormatterAgent formatterAgent,
        ChatRecordRepository chatRecordRepository,
        ObjectMapper objectMapper
    ) {
        this.paperService = paperService;
        this.retrieverAgent = retrieverAgent;
        this.answerAgent = answerAgent;
        this.citationVerifierAgent = citationVerifierAgent;
        this.formatterAgent = formatterAgent;
        this.chatRecordRepository = chatRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, User owner) {
        Instant started = Instant.now();
        Paper paper = request.paperId() == null ? null : paperService.requireOwnedPaper(request.paperId(), owner.getId());
        List<SourceResponse> sources = paper == null
            ? retrieverAgent.retrieveLibrary(owner, request.question(), request.useRag())
            : retrieverAgent.retrieve(paper, request.question(), request.useRag());
        GeneratedAnswer generated = answerAgent.answer(paper, request.question(), sources);
        String verified = citationVerifierAgent.verify(generated.content(), sources);
        String formatted = formatterAgent.format(verified);
        int latencyMs = Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));

        ChatRecord record = new ChatRecord();
        record.setOwner(owner);
        record.setPaper(paper);
        record.setQuestion(request.question().trim());
        record.setAnswer(formatted);
        record.setSourcesJson(toJson(sources));
        record.setModelName(generated.modelName());
        record.setLatencyMs(latencyMs);
        ChatRecord saved = chatRecordRepository.save(record);

        return new ChatResponse(formatted, sources, saved.getId(), generated.modelName(), latencyMs);
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
}
