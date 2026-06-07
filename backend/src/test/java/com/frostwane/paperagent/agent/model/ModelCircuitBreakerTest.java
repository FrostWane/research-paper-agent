package com.frostwane.paperagent.agent.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCircuitBreakerTest {

    @Test
    void opensAfterConsecutiveFailuresAndBlocksCallsDuringCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(clock);

        breaker.markFailure("target-a");
        breaker.markFailure("target-a");
        breaker.markFailure("target-a");

        ModelCircuitBreaker.CircuitSnapshot snapshot = breaker.snapshot("target-a");
        assertThat(snapshot.state()).isEqualTo("OPEN");
        assertThat(snapshot.consecutiveFailures()).isEqualTo(3);
        assertThat(snapshot.openUntil()).isNotNull();

        ModelCircuitBreaker.CallDecision decision = breaker.beforeCall("target-a");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.circuitState()).isEqualTo("OPEN");
    }

    @Test
    void halfOpenAllowsSingleProbeAndSuccessClosesCircuit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(clock);
        open(breaker, "target-b");
        clock.advance(Duration.ofSeconds(61));

        ModelCircuitBreaker.CallDecision probe = breaker.beforeCall("target-b");
        assertThat(probe.allowed()).isTrue();
        assertThat(probe.circuitState()).isEqualTo("HALF_OPEN");

        ModelCircuitBreaker.CallDecision concurrentProbe = breaker.beforeCall("target-b");
        assertThat(concurrentProbe.allowed()).isFalse();
        assertThat(concurrentProbe.circuitState()).isEqualTo("HALF_OPEN");

        breaker.markSuccess("target-b");

        ModelCircuitBreaker.CircuitSnapshot snapshot = breaker.snapshot("target-b");
        assertThat(snapshot.state()).isEqualTo("CLOSED");
        assertThat(snapshot.consecutiveFailures()).isZero();
        assertThat(snapshot.openUntil()).isNull();
    }

    @Test
    void halfOpenFailureReopensCircuit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(clock);
        open(breaker, "target-c");
        clock.advance(Duration.ofSeconds(61));

        assertThat(breaker.beforeCall("target-c").allowed()).isTrue();
        breaker.markFailure("target-c");

        ModelCircuitBreaker.CircuitSnapshot snapshot = breaker.snapshot("target-c");
        assertThat(snapshot.state()).isEqualTo("OPEN");
        assertThat(snapshot.consecutiveFailures()).isEqualTo(4);
        assertThat(snapshot.openUntil()).isNotNull();
    }

    @Test
    void resetClosesOpenCircuitAndClearsFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(clock);
        open(breaker, "target-d");

        ModelCircuitBreaker.CircuitSnapshot reset = breaker.reset("target-d");

        assertThat(reset.state()).isEqualTo("CLOSED");
        assertThat(reset.consecutiveFailures()).isZero();
        assertThat(reset.openUntil()).isNull();
        assertThat(breaker.beforeCall("target-d").allowed()).isTrue();
    }

    @Test
    void resetClearsHalfOpenProbeInFlight() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(clock);
        open(breaker, "target-e");
        clock.advance(Duration.ofSeconds(61));
        assertThat(breaker.beforeCall("target-e").circuitState()).isEqualTo("HALF_OPEN");

        breaker.reset("target-e");

        ModelCircuitBreaker.CallDecision decision = breaker.beforeCall("target-e");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.circuitState()).isEqualTo("CLOSED");
    }

    private void open(ModelCircuitBreaker breaker, String target) {
        breaker.markFailure(target);
        breaker.markFailure(target);
        breaker.markFailure(target);
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
