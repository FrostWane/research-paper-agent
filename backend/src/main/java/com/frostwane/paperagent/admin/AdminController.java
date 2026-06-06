package com.frostwane.paperagent.admin;

import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.IntentRouteResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.QueryTermMappingResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.RagSettingsResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.UserStatusUpdateRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.SamplePromptResponse;
import com.frostwane.paperagent.agent.intent.IntentRouteService;
import com.frostwane.paperagent.agent.sample.SamplePromptService;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SamplePromptService samplePromptService;
    private final RagSettingsService ragSettingsService;
    private final IntentRouteService intentRouteService;
    private final CurrentUserService currentUserService;

    public AdminController(
        AdminService adminService,
        SamplePromptService samplePromptService,
        RagSettingsService ragSettingsService,
        IntentRouteService intentRouteService,
        CurrentUserService currentUserService
    ) {
        this.adminService = adminService;
        this.samplePromptService = samplePromptService;
        this.ragSettingsService = ragSettingsService;
        this.intentRouteService = intentRouteService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(adminService.overview(currentUserService.getRequiredUser()));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> users() {
        return ApiResponse.ok(adminService.users(currentUserService.getRequiredUser()));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.ok(adminService.updateUserStatus(id, request.status(), currentUserService.getRequiredUser()));
    }

    @GetMapping("/query-term-mappings")
    public ApiResponse<List<QueryTermMappingResponse>> queryTermMappings() {
        return ApiResponse.ok(adminService.queryTermMappings(currentUserService.getRequiredUser()));
    }

    @GetMapping("/rag-settings")
    public ApiResponse<RagSettingsResponse> ragSettings() {
        return ApiResponse.ok(ragSettingsService.get(currentUserService.getRequiredUser()));
    }

    @PatchMapping("/rag-settings")
    public ApiResponse<RagSettingsResponse> updateRagSettings(@Valid @RequestBody RagSettingsRequest request) {
        return ApiResponse.ok(ragSettingsService.update(request, currentUserService.getRequiredUser()));
    }

    @GetMapping("/intent-routes")
    public ApiResponse<List<IntentRouteResponse>> intentRoutes() {
        return ApiResponse.ok(intentRouteService.list(currentUserService.getRequiredUser()));
    }

    @PostMapping("/intent-routes")
    public ApiResponse<IntentRouteResponse> createIntentRoute(@Valid @RequestBody IntentRouteRequest request) {
        return ApiResponse.ok(intentRouteService.create(request, currentUserService.getRequiredUser()));
    }

    @PatchMapping("/intent-routes/{id}")
    public ApiResponse<IntentRouteResponse> updateIntentRoute(@PathVariable Long id, @Valid @RequestBody IntentRouteRequest request) {
        return ApiResponse.ok(intentRouteService.update(id, request, currentUserService.getRequiredUser()));
    }

    @DeleteMapping("/intent-routes/{id}")
    public ApiResponse<Void> deleteIntentRoute(@PathVariable Long id) {
        intentRouteService.delete(id, currentUserService.getRequiredUser());
        return ApiResponse.empty();
    }

    @PostMapping("/query-term-mappings")
    public ApiResponse<QueryTermMappingResponse> createQueryTermMapping(@Valid @RequestBody QueryTermMappingRequest request) {
        return ApiResponse.ok(adminService.createQueryTermMapping(request, currentUserService.getRequiredUser()));
    }

    @PatchMapping("/query-term-mappings/{id}")
    public ApiResponse<QueryTermMappingResponse> updateQueryTermMapping(@PathVariable Long id, @Valid @RequestBody QueryTermMappingRequest request) {
        return ApiResponse.ok(adminService.updateQueryTermMapping(id, request, currentUserService.getRequiredUser()));
    }

    @DeleteMapping("/query-term-mappings/{id}")
    public ApiResponse<Void> deleteQueryTermMapping(@PathVariable Long id) {
        adminService.deleteQueryTermMapping(id, currentUserService.getRequiredUser());
        return ApiResponse.empty();
    }

    @GetMapping("/sample-prompts")
    public ApiResponse<List<SamplePromptResponse>> samplePrompts() {
        return ApiResponse.ok(samplePromptService.listAll(currentUserService.getRequiredUser()));
    }

    @PostMapping("/sample-prompts")
    public ApiResponse<SamplePromptResponse> createSamplePrompt(@Valid @RequestBody SamplePromptRequest request) {
        return ApiResponse.ok(samplePromptService.create(request, currentUserService.getRequiredUser()));
    }

    @PatchMapping("/sample-prompts/{id}")
    public ApiResponse<SamplePromptResponse> updateSamplePrompt(@PathVariable Long id, @Valid @RequestBody SamplePromptRequest request) {
        return ApiResponse.ok(samplePromptService.update(id, request, currentUserService.getRequiredUser()));
    }

    @DeleteMapping("/sample-prompts/{id}")
    public ApiResponse<Void> deleteSamplePrompt(@PathVariable Long id) {
        samplePromptService.delete(id, currentUserService.getRequiredUser());
        return ApiResponse.empty();
    }
}
