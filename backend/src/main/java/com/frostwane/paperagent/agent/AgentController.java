package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatFeedbackRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionCreateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionUpdateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamTaskResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptResponse;
import com.frostwane.paperagent.agent.sample.SamplePromptService;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.common.IdempotencyService;
import com.frostwane.paperagent.user.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class AgentController {

    private final AgentOrchestratorService orchestratorService;
    private final AgentStreamService agentStreamService;
    private final SamplePromptService samplePromptService;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;

    public AgentController(
        AgentOrchestratorService orchestratorService,
        AgentStreamService agentStreamService,
        SamplePromptService samplePromptService,
        CurrentUserService currentUserService,
        IdempotencyService idempotencyService
    ) {
        this.orchestratorService = orchestratorService;
        this.agentStreamService = agentStreamService;
        this.samplePromptService = samplePromptService;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/api/agent/chat")
    public ApiResponse<ChatResponse> chat(
        @Valid @RequestBody ChatRequest request,
        @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserService.getRequiredUser();
        return ApiResponse.ok(idempotencyService.run(
            user,
            "POST /api/agent/chat",
            idempotencyKey,
            request,
            ChatResponse.class,
            () -> orchestratorService.chat(request, user)
        ));
    }

    @PostMapping(value = "/api/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        return agentStreamService.stream(request, currentUserService.getRequiredUser());
    }

    @GetMapping("/api/agent/chat/stream/tasks")
    public ApiResponse<List<ChatStreamTaskResponse>> activeStreamTasks() {
        return ApiResponse.ok(agentStreamService.activeTasks(currentUserService.getRequiredUser()));
    }

    @PostMapping("/api/agent/chat/stream/tasks/{taskId}/cancel")
    public ApiResponse<ChatStreamTaskResponse> cancelStreamTask(@PathVariable String taskId) {
        return ApiResponse.ok(agentStreamService.cancel(taskId, currentUserService.getRequiredUser()));
    }

    @GetMapping("/api/papers/{paperId}/chats")
    public ApiResponse<List<ChatRecordResponse>> chats(@PathVariable Long paperId) {
        return ApiResponse.ok(orchestratorService.listChats(paperId, currentUserService.getRequiredUser()));
    }

    @GetMapping("/api/agent/chats")
    public ApiResponse<List<ChatRecordResponse>> libraryChats() {
        return ApiResponse.ok(orchestratorService.listLibraryChats(currentUserService.getRequiredUser()));
    }

    @GetMapping("/api/agent/sessions")
    public ApiResponse<List<ChatSessionResponse>> sessions(@RequestParam(required = false) Long paperId) {
        return ApiResponse.ok(orchestratorService.listSessions(paperId, currentUserService.getRequiredUser()));
    }

    @PostMapping("/api/agent/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        return ApiResponse.ok(orchestratorService.createSession(request, currentUserService.getRequiredUser()));
    }

    @PatchMapping("/api/agent/sessions/{id}")
    public ApiResponse<ChatSessionResponse> updateSession(@PathVariable Long id, @Valid @RequestBody ChatSessionUpdateRequest request) {
        return ApiResponse.ok(orchestratorService.updateSession(id, request, currentUserService.getRequiredUser()));
    }

    @GetMapping("/api/agent/sessions/{id}/chats")
    public ApiResponse<List<ChatRecordResponse>> sessionChats(@PathVariable Long id) {
        return ApiResponse.ok(orchestratorService.listSessionChats(id, currentUserService.getRequiredUser()));
    }

    @GetMapping("/api/agent/sample-prompts")
    public ApiResponse<List<SamplePromptResponse>> samplePrompts(@RequestParam(defaultValue = "LIBRARY") String scope) {
        return ApiResponse.ok(samplePromptService.listEnabled(scope));
    }

    @PatchMapping("/api/agent/chats/{id}/feedback")
    public ApiResponse<ChatRecordResponse> feedback(@PathVariable Long id, @Valid @RequestBody ChatFeedbackRequest request) {
        return ApiResponse.ok(orchestratorService.feedback(id, request, currentUserService.getRequiredUser()));
    }
}
