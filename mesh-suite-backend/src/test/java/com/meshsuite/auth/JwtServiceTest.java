package com.meshsuite.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("test-secret-test-secret-test-secret-32b");

    @Test
    void generatesTokenWithExpectedClaimsAnd8hExpiryByDefault() {
        UUID usuarioId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();

        String token = jwtService.generateToken(usuarioId, tenantId, empresaId, "ADMINISTRADOR", false);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(usuarioId.toString());
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("empresa_id", String.class)).isEqualTo(empresaId.toString());
        assertThat(claims.get("papel", String.class)).isEqualTo("ADMINISTRADOR");

        long hoursUntilExpiry = java.time.Duration.between(Instant.now(), claims.getExpiration().toInstant()).toHours();
        assertThat(hoursUntilExpiry).isBetween(7L, 8L);
    }

    @Test
    void grantsThirtyDayExpiryWhenManterConectado() {
        String token = jwtService.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ADMINISTRADOR", true);
        Claims claims = jwtService.parseClaims(token);

        long daysUntilExpiry = java.time.Duration.between(Instant.now(), claims.getExpiration().toInstant()).toDays();
        assertThat(daysUntilExpiry).isBetween(29L, 30L);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService("different-secret-different-secret-32b");
        String token = other.generateToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ADMINISTRADOR", false);

        assertThrows(SignatureException.class, () -> jwtService.parseClaims(token));
    }
}
