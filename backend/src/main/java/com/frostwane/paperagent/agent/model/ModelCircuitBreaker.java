package com.frostwane.paperagent.agent.model;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
public class ModelCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, ModelHealth> healthByTarget = new HashMap<>();

    public ModelCircuitBreaker() {
        this(Clock.systemUTC());
    }

    ModelCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    public synchronized CallDecision beforeCall(String targetName) {
        String key = key(targetName);
        ModelHealth health = healthByTarget.computeIfAbsent(key, ignored -> new ModelHealth());
        Instant now = clock.instant();
        if (health.state == CircuitState.OPEN) {
            if (health.openUntil != null && now.isBefore(health.openUntil)) {
                return CallDecision.blocked(health.state.name(), openUntil(health), "模型目标已熔断，等待冷却结束。");
            }
            health.state = CircuitState.HALF_OPEN;
            health.halfOpenInFlight = true;
            return CallDecision.allowed(health.state.name(), openUntil(health));
        }
        if (health.state == CircuitState.HALF_OPEN) {
            if (health.halfOpenInFlight) {
                return CallDecision.blocked(health.state.name(), openUntil(health), "半开探测正在进行，本次调用跳过。");
            }
            health.halfOpenInFlight = true;
            return CallDecision.allowed(health.state.name(), openUntil(health));
        }
        return CallDecision.allowed(health.state.name(), null);
    }

    public synchronized void markSuccess(String targetName) {
        ModelHealth health = healthByTarget.computeIfAbsent(key(targetName), ignored -> new ModelHealth());
        health.state = CircuitState.CLOSED;
        health.consecutiveFailures = 0;
        health.openUntil = null;
        health.halfOpenInFlight = false;
    }

    public synchronized void markFailure(String targetName) {
        ModelHealth health = healthByTarget.computeIfAbsent(key(targetName), ignored -> new ModelHealth());
        health.consecutiveFailures++;
        health.halfOpenInFlight = false;
        if (health.state == CircuitState.HALF_OPEN || health.consecutiveFailures >= FAILURE_THRESHOLD) {
            health.state = CircuitState.OPEN;
            health.openUntil = clock.instant().plus(OPEN_DURATION);
            return;
        }
        health.state = CircuitState.CLOSED;
        health.openUntil = null;
    }

    public synchronized CircuitSnapshot snapshot(String targetName) {
        ModelHealth health = healthByTarget.get(key(targetName));
        if (health == null) {
            return new CircuitSnapshot(CircuitState.CLOSED.name(), 0, null);
        }
        return new CircuitSnapshot(health.state.name(), health.consecutiveFailures, openUntil(health));
    }

    private String key(String targetName) {
        return targetName == null || targetName.isBlank() ? "unknown" : targetName.trim();
    }

    private OffsetDateTime openUntil(ModelHealth health) {
        return health.openUntil == null ? null : OffsetDateTime.ofInstant(health.openUntil, ZoneOffset.UTC);
    }

    private enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static class ModelHealth {
        private CircuitState state = CircuitState.CLOSED;
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean halfOpenInFlight;
    }

    public record CallDecision(
        boolean allowed,
        String circuitState,
        OffsetDateTime openUntil,
        String reason
    ) {
        static CallDecision allowed(String circuitState, OffsetDateTime openUntil) {
            return new CallDecision(true, circuitState, openUntil, null);
        }

        static CallDecision blocked(String circuitState, OffsetDateTime openUntil, String reason) {
            return new CallDecision(false, circuitState, openUntil, reason);
        }
    }

    public record CircuitSnapshot(
        String state,
        int consecutiveFailures,
        OffsetDateTime openUntil
    ) {
    }
}
