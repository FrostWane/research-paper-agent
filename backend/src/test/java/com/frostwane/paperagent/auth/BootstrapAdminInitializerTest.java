package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.config.PaperAgentProperties;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRepository;
import com.frostwane.paperagent.user.UserRole;
import com.frostwane.paperagent.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminInitializerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminWhenNoAdminExists() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("admin@example.local")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        newInitializer(repository, properties()).run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@example.local");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(passwordEncoder.matches("Admin@123456", saved.getPasswordHash())).isTrue();
    }

    @Test
    void skipsWhenAdminAlreadyExists() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(true);
        when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());

        newInitializer(repository, properties()).run(mock(ApplicationArguments.class));

        verify(repository, never()).save(any(User.class));
    }

    @Test
    void createsConfiguredAdminWhenOtherAdminExistsAndResetEnabled() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(true);
        when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("admin@example.local")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperAgentProperties properties = properties();
        properties.getBootstrapAdmin().setResetPassword(true);
        newInitializer(repository, properties).run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@example.local");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(passwordEncoder.matches("Admin@123456", saved.getPasswordHash())).isTrue();
    }

    @Test
    void resetsConfiguredAdminPasswordWhenEnabled() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@example.local");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.DISABLED);
        admin.setPasswordHash(passwordEncoder.encode("old-password"));

        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(true);
        when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));

        PaperAgentProperties properties = properties();
        properties.getBootstrapAdmin().setResetPassword(true);
        newInitializer(repository, properties).run(mock(ApplicationArguments.class));

        verify(repository).save(admin);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(passwordEncoder.matches("Admin@123456", admin.getPasswordHash())).isTrue();
    }

    private BootstrapAdminInitializer newInitializer(UserRepository repository, PaperAgentProperties properties) {
        return new BootstrapAdminInitializer(properties, repository, passwordEncoder);
    }

    private PaperAgentProperties properties() {
        return new PaperAgentProperties();
    }
}
