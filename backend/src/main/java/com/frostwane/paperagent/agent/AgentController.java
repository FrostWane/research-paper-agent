package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRecordResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgentController {

    private final AgentOrchestratorService orchestratorService;
    private final CurrentUserService currentUserService;

    public AgentController(AgentOrchestratorService orchestratorService, CurrentUserService currentUserService) {
        this.orchestratorService = orchestratorService;
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
}
