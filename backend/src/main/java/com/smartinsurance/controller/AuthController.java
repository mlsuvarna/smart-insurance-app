package com.smartinsurance.controller;

import com.smartinsurance.dto.AuthDtos.AuthResponse;
import com.smartinsurance.dto.AuthDtos.LoginRequest;
import com.smartinsurance.dto.AuthDtos.PasswordChangeRequest;
import com.smartinsurance.dto.AuthDtos.RegisterRequest;
import com.smartinsurance.dto.AuthDtos.UserResponse;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/register")
    @Operation(security = {})
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @PostMapping("/login")
    @Operation(security = {})
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
    @PutMapping("/password")
    public void changePassword(@AuthenticationPrincipal AppUser user, @Valid @RequestBody PasswordChangeRequest request) {
        auth.changePassword(user, request);
    }
}
