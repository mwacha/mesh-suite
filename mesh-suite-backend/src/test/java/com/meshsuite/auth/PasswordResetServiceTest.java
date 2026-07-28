package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock AuthService authService;
    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock MailService mailService;

    private PasswordResetService service() {
        PasswordResetService svc = new PasswordResetService(tokenRepository, usuarioRepository, authService,
                mailService, org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        // Plain Mockito test, no Spring proxy in play: `self` (package-private,
        // @Autowired @Lazy in production -- see PasswordResetService) is simulated
        // by pointing it back at the same instance. These tests cover business
        // logic only, not the AOP/transaction behavior self-injection exists to fix.
        svc.self = svc;
        return svc;
    }

    @Test
    void requestResetSendsEmailWhenUsuarioExists() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("marina@aurora.com.br");
        usuario.setAtivo(true);
        when(authService.findByEmailForLogin("marina@aurora.com.br")).thenReturn(usuario);

        boolean found = service().requestReset("marina@aurora.com.br");

        assertTrue(found);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void requestResetDoesNothingSilentlyWhenUsuarioDoesNotExist() {
        when(authService.findByEmailForLogin("ninguem@aurora.com.br")).thenReturn(null);

        boolean found = service().requestReset("ninguem@aurora.com.br");

        assertFalse(found);
        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void confirmResetRejectsExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(sha256("raw-token"));
        token.setExpiraEm(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmReset("raw-token", "novaSenha123"));
    }

    @Test
    void confirmResetRejectsAlreadyUsedToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(sha256("raw-token"));
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        token.setUsadoEm(Instant.now());
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(AuthException.class, () -> service().confirmReset("raw-token", "novaSenha123"));
    }

    private static String sha256(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
