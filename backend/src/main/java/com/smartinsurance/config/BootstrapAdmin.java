package com.smartinsurance.config;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String email;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String password;

    public BootstrapAdmin(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() != password.isBlank()) {
            throw new IllegalStateException("Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or neither");
        }
        if (!email.isBlank() && !users.existsByEmailIgnoreCase(email)) {
            if (password.length() < 16) {
                throw new IllegalStateException("Bootstrap administrator password must be at least 16 characters");
            }
            users.save(new AppUser(email, encoder.encode(password), Role.ADMIN, "System Administrator"));
        }
    }
}
