package com.meshsuite.auth.service;

import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.domain.PasswordResetToken;
import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.repository.PasswordResetTokenRepository;
import com.meshsuite.mail.service.MailService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    // class that sets app.bypass_tenant_check. Since e-mail is unique per tenant
    // (not globally), this can legitimately match more than one account -- issues
    // one reset token/e-mail per active account rather than assuming a single row.
    public boolean requestReset(String email) {
        List<User> users = authService.findAllByEmailForLogin(email).stream()
                .filter(User::isActive)
                .toList();
        if (users.isEmpty()) {
            return false; // caller still returns 200 with the generic message
        }

        users.forEach(user -> issueResetToken(user, email));
        return true;
    }

    private void issueResetToken(User user, String email) {
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
