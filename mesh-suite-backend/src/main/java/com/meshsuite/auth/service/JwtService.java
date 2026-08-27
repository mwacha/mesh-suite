package com.meshsuite.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtService {

    // Distinguishes a pending-account-selection token from a real session token --
    // parsePendingSelectionToken rejects anything without this claim, so a stolen
    // real session cookie can never be replayed against /select-account, and vice
    // versa. Belt-and-braces on top of the two also using different cookie names.
    private static final String PENDING_SELECTION_PURPOSE = "account_selection";

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID usuarioId, UUID tenantId, UUID empresaId, String papel, boolean manterConectado) {
        Instant now = Instant.now();
        Instant expiry = manterConectado ? now.plus(30, ChronoUnit.DAYS) : now.plus(8, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("empresa_id", empresaId.toString())
                .claim("papel", papel)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    // Short-lived (5 min) token proving which accounts already had their password
    // validated by a login call that returned multiple matches -- carries no
    // password, just the user ids the picker is allowed to choose between.
    public String generatePendingSelectionToken(List<UUID> validatedUserIds) {
        Instant now = Instant.now();
        String userIds = validatedUserIds.stream().map(UUID::toString).collect(Collectors.joining(","));

        return Jwts.builder()
                .claim("purpose", PENDING_SELECTION_PURPOSE)
                .claim("user_ids", userIds)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public List<UUID> parsePendingSelectionToken(String token) {
        Claims claims = parseClaims(token);
        if (!PENDING_SELECTION_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("Not an account-selection token");
        }
        return Arrays.stream(claims.get("user_ids", String.class).split(","))
                .map(UUID::fromString)
                .toList();
    }
}
