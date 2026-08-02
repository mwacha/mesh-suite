package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    // Field injection (not constructor), specifically so PasswordResetServiceTest
    // can construct this class directly with mocks and assign `self` manually --
    // see the test. In production, Spring wires this via @Lazy to avoid a
    // circular-construction failure. Package-private (no `private`) so the test,
    // which lives in the same package, can assign it directly.
    @Autowired
    @Lazy
    PasswordResetService self;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                 AuthService authService, MailService mailService, PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    // User lookups pre-tenant-context always go through AuthService, the one
    // class that sets app.bypass_tenant_check.
    public boolean requestReset(String email) {
        User user = authService.findByEmailForLogin(email);
        if (user == null || !user.isActive()) {
            return false; // caller still returns 200 with the generic message
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(token); // PasswordResetToken has no RLS -- no tenant context needed here

        String resetLink = "https://app.meshsuite.local/redefinir-senha?token=" + rawToken;
        mailService.sendPasswordResetEmail(email, resetLink);
        return true;
    }

    public void confirmReset(String rawToken, String novaSenha) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthException::new);

        if (token.getUsadoEm() != null || Instant.now().isAfter(token.getExpiraEm())) {
            throw new AuthException();
        }

        User user = authService.findUserByIdBypassingTenant(token.getUserId());
        if (user == null) {
            throw new AuthException();
        }

        // app_user has RLS: updating password_hash needs app.tenant_id set to this
        // row's tenant. The bypass lookup above told us which tenant; route the
        // write through `self.` so TenantContextAspect actually applies.
        TenantContext.set(user.getTenantId());
        try {
            self.updateSenhaAndMarkTokenUsed(user, novaSenha, token);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void updateSenhaAndMarkTokenUsed(User user, String novaSenha, PasswordResetToken token) {
        user.setPasswordHash(passwordEncoder.encode(novaSenha));
        userRepository.save(user);

        token.setUsadoEm(Instant.now());
        tokenRepository.save(token);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
