package com.meshsuite.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.meshsuite.auth.domain.PasswordResetToken;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.PasswordResetTokenRepository;
import com.meshsuite.auth.service.AuthService;
import com.meshsuite.auth.service.PasswordResetService;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock AuthService authService;
    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock MailService mailService;

    private PasswordResetService service() {
        PasswordResetService svc = new PasswordResetService(tokenRepository, userRepository, authService,
                mailService, org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        // Plain Mockito test, no Spring proxy in play: `self` (package-private,
        // @Autowired @Lazy in production -- see PasswordResetService) is simulated
        // by pointing it back at the same instance. These tests cover business
        // logic only, not the AOP/transaction behavior self-injection exists to fix.
        svc.self = svc;
        return svc;
    }

    @Test
    void requestResetSendsEmailWhenUserExists() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("marina@aurora.com.br");
        user.setActive(true);
        when(authService.findAllByEmailForLogin("marina@aurora.com.br")).thenReturn(List.of(user));

        boolean found = service().requestReset("marina@aurora.com.br");

        assertTrue(found);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetEmail(eq("marina@aurora.com.br"), any());
    }

    @Test
    void requestResetSendsOneEmailPerAccountWhenTheSameAddressMatchesSeveralTenants() {
        User marinaAurora = new User();
        marinaAurora.setId(UUID.randomUUID());
        marinaAurora.setEmail("marcus@aurora.com.br");
        marinaAurora.setActive(true);
        User marcusLindaBrasil = new User();
        marcusLindaBrasil.setId(UUID.randomUUID());
        marcusLindaBrasil.setEmail("marcus@aurora.com.br");
        marcusLindaBrasil.setActive(true);
        when(authService.findAllByEmailForLogin("marcus@aurora.com.br"))
                .thenReturn(List.of(marinaAurora, marcusLindaBrasil));

        boolean found = service().requestReset("marcus@aurora.com.br");

        assertTrue(found);
        verify(tokenRepository, times(2)).save(any(PasswordResetToken.class));
        verify(mailService, times(2)).sendPasswordResetEmail(eq("marcus@aurora.com.br"), any());
    }

    @Test
    void requestResetDoesNothingSilentlyWhenUserDoesNotExist() {
        when(authService.findAllByEmailForLogin("ninguem@aurora.com.br")).thenReturn(List.of());

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
