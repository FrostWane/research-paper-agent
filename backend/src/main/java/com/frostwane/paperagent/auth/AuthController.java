package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.auth.dto.AuthDtos.AuthResponse;
import com.frostwane.paperagent.auth.dto.AuthDtos.LoginRequest;
import com.frostwane.paperagent.auth.dto.AuthDtos.RegisterRequest;
import com.frostwane.paperagent.auth.dto.AuthDtos.UserResponse;
import com.frostwane.paperagent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(authService.currentUser(currentUserService.getRequiredUser()));
    }
}
