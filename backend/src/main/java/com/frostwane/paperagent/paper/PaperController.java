package com.frostwane.paperagent.paper;

import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.paper.dto.PaperDtos.PaperRequest;
import com.frostwane.paperagent.paper.dto.PaperDtos.PaperResponse;
import com.frostwane.paperagent.paper.dto.PaperDtos.ParseStatusResponse;
import com.frostwane.paperagent.paper.dto.PaperDtos.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;
    private final CurrentUserService currentUserService;

    public PaperController(PaperService paperService, CurrentUserService currentUserService) {
        this.paperService = paperService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PaperResponse>> list(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(paperService.list(currentUserService.getRequiredUser(), keyword, status, page, pageSize));
    }

    @PostMapping
    public ApiResponse<PaperResponse> create(@Valid @RequestBody PaperRequest request) {
        return ApiResponse.ok(paperService.create(request, currentUserService.getRequiredUser()));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(paperService.get(id, currentUserService.getRequiredUser()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaperResponse> update(@PathVariable Long id, @Valid @RequestBody PaperRequest request) {
        return ApiResponse.ok(paperService.update(id, request, currentUserService.getRequiredUser()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paperService.delete(id, currentUserService.getRequiredUser());
        return ApiResponse.empty();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PaperResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(paperService.updateStatus(id, request.status(), currentUserService.getRequiredUser()));
    }

    @GetMapping("/{id}/parse-status")
    public ApiResponse<ParseStatusResponse> parseStatus(@PathVariable Long id) {
        return ApiResponse.ok(paperService.parseStatus(id, currentUserService.getRequiredUser()));
    }
}
