package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.config.PaperAgentProperties;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRepository;
import com.frostwane.paperagent.user.UserRole;
import com.frostwane.paperagent.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final PaperAgentProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
        PaperAgentProperties properties,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        PaperAgentProperties.BootstrapAdmin config = properties.getBootstrapAdmin();
        if (config == null || !config.isEnabled()) {
            return;
        }
        String username = required(config.getUsername(), "bootstrap admin username");
        String email = required(config.getEmail(), "bootstrap admin email").toLowerCase();
        String password = required(config.getPassword(), "bootstrap admin password");
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Bootstrap admin password must be at least 8 characters");
        }

        Optional<User> configuredUser = userRepository.findByUsernameIgnoreCase(username);
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            maybeResetOrCreateConfiguredAdmin(config, configuredUser, username, email, password);
            return;
        }
        User admin = configuredUser.orElseGet(() -> createUser(username, email, password));
        if (configuredUser.isEmpty()) {
            ensureEmailAvailable(email, username);
        }
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.NORMAL);
        admin.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(admin);
        log.info("Bootstrap admin account is ready: {}", username);
    }

    private void maybeResetOrCreateConfiguredAdmin(
        PaperAgentProperties.BootstrapAdmin config,
        Optional<User> configuredUser,
        String username,
        String email,
        String password
    ) {
        if (!config.isResetPassword()) {
            return;
        }
        if (configuredUser.isEmpty()) {
            ensureEmailAvailable(email, username);
            User admin = createUser(username, email, password);
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.NORMAL);
            userRepository.save(admin);
            log.info("Bootstrap admin account is ready: {}", username);
            return;
        }
        User user = configuredUser.get();
        user.setRole(UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.NORMAL);
        userRepository.save(user);
        log.info("Bootstrap admin password reset for account: {}", user.getUsername());
    }

    private User createUser(String username, String email, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }

    private void ensureEmailAvailable(String email, String username) {
        userRepository.findByEmailIgnoreCase(email)
            .filter(existing -> !username.equalsIgnoreCase(existing.getUsername()))
            .ifPresent(existing -> {
                throw new IllegalStateException("Bootstrap admin email is already used by another account");
            });
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must not be blank");
        }
        return value.trim();
    }
}
