package com.frostwane.paperagent.admin;

import com.frostwane.paperagent.admin.dto.AdminDtos.AdminOverviewResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.AdminUserResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.UserStatusUpdateRequest;
import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final CurrentUserService currentUserService;

    public AdminController(AdminService adminService, CurrentUserService currentUserService) {
        this.adminService = adminService;
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
}
