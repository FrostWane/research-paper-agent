package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolSettingServiceTest {

    @Test
    void availableRejectsUserWhenToolRequiresAdmin() {
        AgentToolSetting setting = new AgentToolSetting();
        setting.setToolName("library-stats");
        setting.setEnabled(true);
        setting.setMinimumRole(UserRole.ADMIN);
        AgentToolSettingRepository repository = mock(AgentToolSettingRepository.class);
        when(repository.findById("library-stats")).thenReturn(Optional.of(setting));

        AgentToolSettingService service = new AgentToolSettingService(repository);

        assertThat(service.available("library-stats", UserRole.USER)).isFalse();
        assertThat(service.available("library-stats", UserRole.ADMIN)).isTrue();
    }

    @Test
    void updateMinimumRolePersistsSetting() {
        AgentToolSettingRepository repository = mock(AgentToolSettingRepository.class);
        when(repository.findById("library-stats")).thenReturn(Optional.empty());
        when(repository.save(any(AgentToolSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentToolSettingService service = new AgentToolSettingService(repository);

        assertThat(service.updateMinimumRole("library-stats", UserRole.ADMIN)).isEqualTo(UserRole.ADMIN);
    }
}
