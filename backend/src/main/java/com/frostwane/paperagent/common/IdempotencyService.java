package com.frostwane.paperagent.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class IdempotencyService {

    public static final String HEADER = "X-Idempotency-Key";

    private static final int KEY_MAX_LENGTH = 160;
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,160}");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T run(
        User owner,
        String endpoint,
        String idempotencyKey,
        Object fingerprint,
        Class<T> responseType,
        Supplier<T> action
    ) {
        String key = normalizeKey(idempotencyKey);
        if (key == null) {
            return action.get();
        }
        Long ownerId = owner == null ? null : owner.getId();
        if (ownerId == null) {
            return action.get();
        }
        String normalizedEndpoint = compact(endpoint, 160);
        String requestHash = hash(fingerprint);
        cleanupExpired(ownerId, normalizedEndpoint, key);
        int inserted = jdbcTemplate.update("""
            insert into request_idempotency(owner_id, endpoint, request_key, request_hash, response_type, status, expires_at)
            values (?, ?, ?, ?, ?, 'IN_PROGRESS', now() + interval '30 minutes')
            on conflict do nothing
            """,
            ownerId,
            normalizedEndpoint,
            key,
            requestHash,
            responseType.getName()
        );
        if (inserted == 0) {
            return replay(ownerId, normalizedEndpoint, key, requestHash, responseType);
        }
        try {
            T response = action.get();
            jdbcTemplate.update("""
                update request_idempotency
                set status = 'COMPLETED',
                    response_json = ?::jsonb,
                    response_type = ?,
                    completed_at = now(),
                    expires_at = now() + interval '24 hours'
                where owner_id = ?
                  and endpoint = ?
                  and request_key = ?
                  and request_hash = ?
                """,
                toJson(response),
                responseType.getName(),
                ownerId,
                normalizedEndpoint,
                key,
                requestHash
            );
            return response;
        } catch (RuntimeException ex) {
            jdbcTemplate.update("""
                delete from request_idempotency
                where owner_id = ?
                  and endpoint = ?
                  and request_key = ?
                  and request_hash = ?
                  and status = 'IN_PROGRESS'
                """,
                ownerId,
                normalizedEndpoint,
                key,
                requestHash
            );
            throw ex;
        }
    }

    private <T> T replay(Long ownerId, String endpoint, String key, String requestHash, Class<T> responseType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select request_hash, status, response_json::text as response_json
            from request_idempotency
            where owner_id = ?
              and endpoint = ?
              and request_key = ?
            """,
            ownerId,
            endpoint,
            key
        );
        if (rows.isEmpty()) {
            throw new BusinessException("幂等请求状态不存在，请重新提交");
        }
        Map<String, Object> row = rows.get(0);
        IdempotencySnapshot snapshot = new IdempotencySnapshot(
            stringValue(row.get("request_hash")),
            stringValue(row.get("status")),
            stringValue(row.get("response_json"))
        );
        if (!requestHash.equals(snapshot.requestHash())) {
            throw new BusinessException("幂等 Key 已被不同请求使用，请更换 Key 后重试");
        }
        if (!"COMPLETED".equals(snapshot.status())) {
            throw new BusinessException("同一幂等请求正在处理中，请稍后重试");
        }
        try {
            return objectMapper.readValue(snapshot.responseJson(), responseType);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("幂等响应读取失败，请更换 Key 后重试");
        }
    }

    private void cleanupExpired(Long ownerId, String endpoint, String key) {
        jdbcTemplate.update("""
            delete from request_idempotency
            where owner_id = ?
              and endpoint = ?
              and request_key = ?
              and expires_at < now()
            """,
            ownerId,
            endpoint,
            key
        );
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > KEY_MAX_LENGTH || !KEY_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException("幂等 Key 格式无效");
        }
        return trimmed;
    }

    private String hash(Object fingerprint) {
        String json = toJson(fingerprint);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("幂等请求序列化失败");
        }
    }

    private String compact(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "UNKNOWN_ENDPOINT" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record IdempotencySnapshot(
        String requestHash,
        String status,
        String responseJson
    ) {
    }
}
