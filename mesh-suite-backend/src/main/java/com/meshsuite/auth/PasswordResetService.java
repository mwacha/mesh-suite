package com.meshsuite.auth;

import com.meshsuite.mail.MailService;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    // Field injection (not constructor), specifically so PasswordResetServiceTest
    // can construct this class directly with mocks and assign `self` manually --
    // see the test. In production, Spring wires this via @Lazy to avoid a
    // circular-construction failure. Package-private (no `private`) so the test,
    // which lives in the same package, can assign it directly. See plan §"Design
    // decision beyond the spec: self-invocation breaks @Transactional...".
    @Autowired
    @Lazy
    PasswordResetService self;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UsuarioRepository usuarioRepository,
                                 AuthService authService, MailService mailService, PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    // Usuario lookups pre-tenant-context always go through AuthService, the one
    // class that sets app.bypass_tenant_check -- see plan §"Design decision beyond
    // the spec: RLS bypass for login lookup".
    public boolean requestReset(String email) {
        Usuario usuario = authService.findByEmailForLogin(email);
        if (usuario == null || !usuario.isAtivo()) {
            return false; // caller still returns 200 with the generic message
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsuarioId(usuario.getId());
        token.setTokenHash(sha256(rawToken));
        token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(token); // PasswordResetToken has no RLS (Task 5) -- no tenant context needed here

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

        Usuario usuario = authService.findUsuarioByIdBypassingTenant(token.getUsuarioId());
        if (usuario == null) {
            throw new AuthException();
        }

        // usuario has RLS: updating senha_hash needs app.tenant_id set to this row's
        // tenant. The bypass lookup above told us which tenant; route the write
        // through `self.` so TenantContextAspect actually applies (see the
        // self-invocation design note).
        TenantContext.set(usuario.getTenantId());
        try {
            self.updateSenhaAndMarkTokenUsed(usuario, novaSenha, token);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void updateSenhaAndMarkTokenUsed(Usuario usuario, String novaSenha, PasswordResetToken token) {
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

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
