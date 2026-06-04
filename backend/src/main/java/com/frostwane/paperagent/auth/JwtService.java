package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.config.PaperAgentProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final PaperAgentProperties properties;
    private final SecretKey key;

    public JwtService(PaperAgentProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(normalizeSecret(properties.getJwt().getSecret()).getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getExpirationMinutes() * 60);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    private String normalizeSecret(String raw) {
        String value = raw == null ? "" : raw;
        if (value.length() >= 32) {
            return value;
        }
        return (value + "--------------------------------").substring(0, 32);
    }
}
