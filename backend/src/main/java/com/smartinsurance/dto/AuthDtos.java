package com.smartinsurance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() { }
    public record RegisterRequest(@NotBlank @Size(max = 160) String fullName,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 12, max = 72) String newPassword) { }
    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) { }
    public record UserResponse(Long id, String email, String fullName, String role) { }
}
