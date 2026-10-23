package com.smartinsurance.service;

import com.smartinsurance.dto.AuthDtos.AuthResponse;
import com.smartinsurance.dto.AuthDtos.LoginRequest;
import com.smartinsurance.dto.AuthDtos.PasswordChangeRequest;
import com.smartinsurance.dto.AuthDtos.RegisterRequest;
import com.smartinsurance.dto.AuthDtos.UserResponse;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.UserRepository;
import com.smartinsurance.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                      AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password()), Role.CUSTOMER,
                request.fullName().trim()));
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email().trim().toLowerCase(), request.password()));
        AppUser user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));
        return response(user);
    }

    @Transactional
    public void changePassword(AppUser user, PasswordChangeRequest request) {
        AppUser stored = users.findById(user.getId())
                .orElseThrow(() -> new com.smartinsurance.exception.ResourceNotFoundException("Account not found"));
        if (!passwordEncoder.matches(request.currentPassword(), stored.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        stored.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwtService.createToken(user), jwtService.getExpirationSeconds(),
                new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name()));
    }
}
