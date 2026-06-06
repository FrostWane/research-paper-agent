package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatFeedbackRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionCreateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatSessionUpdateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptResponse;
import com.frostwane.paperagent.agent.sample.SamplePromptService;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgentController {

    private final AgentOrchestratorService orchestratorService;
    private final SamplePromptService samplePromptService;
    private final CurrentUserService currentUserService;

    public AgentController(
        AgentOrchestratorService orchestratorService,
        SamplePromptService samplePromptService,
        CurrentUserService currentUserService
    ) {
        this.orchestratorService = orchestratorService;
        this.samplePromptService = samplePromptService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/agent/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(orchestratorService.chat(request, currentUserService.getRequiredUser()));
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
