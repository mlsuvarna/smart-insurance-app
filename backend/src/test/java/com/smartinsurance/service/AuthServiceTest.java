package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinsurance.dto.AuthDtos.AuthResponse;
import com.smartinsurance.dto.AuthDtos.LoginRequest;
import com.smartinsurance.dto.AuthDtos.RegisterRequest;
import com.smartinsurance.dto.AuthDtos.PasswordChangeRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.UserRepository;
import com.smartinsurance.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {
    @Test
    void publicRegistrationAlwaysCreatesCustomerAndHashesPassword() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        when(users.existsByEmailIgnoreCase("user@example.test")).thenReturn(false);
        when(encoder.encode("long-password")).thenReturn("bcrypt-hash");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.createToken(any(AppUser.class))).thenReturn("signed-token");
        when(jwt.getExpirationSeconds()).thenReturn(3600L);
        AuthService service = new AuthService(users, encoder, manager, jwt);

        AuthResponse response = service.register(new RegisterRequest("Example User", "USER@example.test", "long-password"));

        assertEquals("CUSTOMER", response.user().role());
        assertEquals("signed-token", response.token());
        verify(encoder).encode("long-password");
    }

    @Test
    void loginAuthenticatesAndReturnsTheStoredCustomer() {
        UserRepository users = mock(UserRepository.class);
        AppUser customer = new AppUser("user@example.test", "bcrypt-hash", Role.CUSTOMER, "Example User");
        ReflectionTestUtils.setField(customer, "id", 12L);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(users.findByEmailIgnoreCase("USER@example.test")).thenReturn(java.util.Optional.of(customer));
        JwtService jwt = mock(JwtService.class);
        when(jwt.createToken(customer)).thenReturn("signed-token");
        when(jwt.getExpirationSeconds()).thenReturn(3600L);
        AuthService service = new AuthService(users, mock(PasswordEncoder.class), manager, jwt);

        AuthResponse response = service.login(new LoginRequest(" USER@example.test ", "password"));

        verify(manager).authenticate(new UsernamePasswordAuthenticationToken("user@example.test", "password"));
        assertEquals("signed-token", response.token());
        assertEquals("CUSTOMER", response.user().role());
    }

    @Test
    void passwordChangeIncrementsSecurityVersionAndRejectsWrongCurrentPassword() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AppUser user = new AppUser("user@example.test", "old-hash", Role.CUSTOMER, "Example User");
        ReflectionTestUtils.setField(user, "id", 17L);
        when(users.findById(17L)).thenReturn(java.util.Optional.of(user));
        when(encoder.matches("old-password", "old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        AuthService service = new AuthService(users, encoder, mock(AuthenticationManager.class), mock(JwtService.class));

        service.changePassword(user, new PasswordChangeRequest("old-password", "new-password"));

        assertEquals(1L, user.getTokenVersion());
        assertEquals("new-hash", user.getPassword());
        when(encoder.matches("wrong", "new-hash")).thenReturn(false);
        assertThrows(BusinessRuleException.class,
                () -> service.changePassword(user, new PasswordChangeRequest("wrong", "another-password")));
        assertEquals(1L, user.getTokenVersion());
    }
}
