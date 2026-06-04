package com.frostwane.paperagent.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 120) String password
    ) {
    }

    public record LoginRequest(
        @NotBlank String account,
        @NotBlank String password
    ) {
    }

    public record AuthResponse(
        String token,
        UserResponse user
    ) {
    }

    public record UserResponse(
        Long id,
        String username,
        String email,
        String avatarUrl,
        String role,
        OffsetDateTime createdAt
    ) {
    }
}
