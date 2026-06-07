package com.frostwane.paperagent.agent.limit;

import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import com.frostwane.paperagent.common.RateLimitException;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRateLimiterServiceTest {

    private final RagSettingsService settingsService = mock(RagSettingsService.class);
    private final AgentRateLimiterService limiterService = new AgentRateLimiterService(settingsService);

    @Test
    void rejectsWhenUserConcurrencyLimitIsReached() {
        when(settingsService.snapshot()).thenReturn(snapshot(true, 10, 1, 20));
        User user = user(1L);

        AgentRateLimitPermit first = limiterService.acquire(user);

        assertThatThrownBy(() -> limiterService.acquire(user))
            .isInstanceOf(RateLimitException.class)
            .hasMessageContaining("已有问答正在运行");

        first.close();
        try (AgentRateLimitPermit ignored = limiterService.acquire(user)) {
            assertThat(limiterService.status().activeGlobal()).isEqualTo(1);
        }
    }

    @Test
    void disabledLimiterReturnsNoopPermit() {
        when(settingsService.snapshot()).thenReturn(snapshot(false, 1, 1, 1));
        User user = user(2L);

        try (AgentRateLimitPermit ignored = limiterService.acquire(user)) {
            assertThat(limiterService.status().activeGlobal()).isZero();
        }
    }

    private RagSettingsSnapshot snapshot(boolean enabled, int globalConcurrency, int userConcurrency, int userPerMinute) {
        return new RagSettingsSnapshot(
            10,
            5,
            520,
            2600,
            1.0d,
            0.78d,
            4,
            2400,
            true,
            6,
            1800,
            true,
            3,
            true,
            false,
            8,
            enabled,
            globalConcurrency,
            userConcurrency,
            userPerMinute
        );
    }

    private User user(Long id) {
        try {
            User user = new User();
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
