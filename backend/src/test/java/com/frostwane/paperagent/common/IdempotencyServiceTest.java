package com.frostwane.paperagent.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    @Test
    void runWithoutKeyBypassesStorage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdempotencyService service = new IdempotencyService(jdbcTemplate, new ObjectMapper());
        AtomicBoolean called = new AtomicBoolean(false);

        String result = service.run(user(), "POST /test", "", "request", String.class, () -> {
            called.set(true);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(called).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void runRejectsInvalidKey() {
        IdempotencyService service = new IdempotencyService(mock(JdbcTemplate.class), new ObjectMapper());

        assertThatThrownBy(() -> service.run(user(), "POST /test", "bad key", "request", String.class, () -> "ok"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("幂等 Key 格式无效");
    }

    private User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(12L);
        return user;
    }
}
