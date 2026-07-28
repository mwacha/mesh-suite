package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately has NO @Transactional anywhere in this class -- see
// AuthControllerNoAmbientTransactionTest (Task 10) for the full rationale, which
// applies identically here. PasswordResetService.confirmReset() calls
// self.updateSenhaAndMarkTokenUsed(...) instead of a bare
// this.updateSenhaAndMarkTokenUsed(...) for exactly the reason AuthService.self exists:
// a real /api/auth/reset-password request has no ambient transaction (AuthController
// isn't @Transactional, and open-in-view is disabled), so a bare `this.` call there
// would bypass Spring's AOP proxy and silently no-op @Transactional (and therefore
// TenantContextAspect) outside of an already-active transaction -- the password/token
// write would appear to succeed (no exception) but never actually persist correctly.
// PasswordResetControllerTest can't catch this: it only exercises /forgot-password.
// PasswordResetServiceTest can't catch it either: it's a plain Mockito test that
// assigns `svc.self = svc` by hand -- the same raw object regardless of whether the
// production `self` field is correctly proxied, so it never exercises a real Spring
// proxy at all. This test reproduces the no-ambient-transaction condition directly, the
// same way AuthControllerNoAmbientTransactionTest does for login: fixture rows
// (including a real PasswordResetToken) are committed for real via an explicit
// TransactionTemplate, then the reset-password call itself runs with zero ambient
// transaction, and afterward the actual persisted state is re-read and asserted on --
// so if `self.` is ever reverted to a bare `this.`, this test (unlike the mock-based
// ones) will actually catch it.
class PasswordResetControllerNoAmbientTransactionTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager txManager;
    @Autowired AuthService authService;

    @Test
    void resetPasswordPersistsWithNoAmbientTransaction() throws Exception {
        // Unique per test run, same rationale as AuthControllerNoAmbientTransactionTest:
        // needs no cleanup afterward and can't collide with any other test class's
        // fixture rows.
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String email = "reset-notx-" + suffix + "@example.com.br";
        String senhaAntiga = "senhaAntiga123";
        String senhaNova = "senhaNova456";
        String cnpj = String.format("%014d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000000L);
        String rawToken = "raw-token-" + suffix;

        UUID[] usuarioId = new UUID[1];

        // A real, separately-committed transaction: this test method itself has no
        // @Transactional, so nothing here gets rolled back, and nothing pre-opens a
        // transaction ahead of the mockMvc call below -- exactly like a real request.
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Tenant tenant = new Tenant();
            tenant.setCodigo("reset-notx-" + suffix);
            tenant.setNome("Reset No-Tx Tenant " + suffix);
            tenantRepository.saveAndFlush(tenant);

            entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

            Empresa empresa = new Empresa();
            empresa.setTenantId(tenant.getId());
            empresa.setRazaoSocial("Reset No-Tx Empresa " + suffix);
            empresa.setCnpj(cnpj);
            empresaRepository.saveAndFlush(empresa);

            Usuario usuario = new Usuario();
            usuario.setTenantId(tenant.getId());
            usuario.setNome("Reset No-Tx Usuario");
            usuario.setEmail(email);
            usuario.setSenhaHash(passwordEncoder.encode(senhaAntiga));
            usuario.setPapel(Papel.ADMINISTRADOR);
            usuarioRepository.saveAndFlush(usuario);
            usuarioId[0] = usuario.getId();

            // PasswordResetToken has no RLS (Task 5) -- no tenant context needed for
            // this insert, but it shares this transaction for convenience.
            PasswordResetToken token = new PasswordResetToken();
            token.setUsuarioId(usuario.getId());
            token.setTokenHash(sha256(rawToken));
            token.setExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
            tokenRepository.save(token);
        });

        // /reset-password isn't rate-limited (only /forgot-password and /login share
        // RateLimiter), so this synthetic IP isn't strictly load-bearing today -- but it
        // costs nothing and keeps this test immune to any future change that ties rate
        // limiting to this endpoint too, matching the pattern
        // AuthControllerNoAmbientTransactionTest already established.
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(request -> {
                            request.setRemoteAddr("10.10.0.4");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"novaSenha\":\"" + senhaNova + "\"}"))
                .andExpect(status().isOk());

        // Verify the effect actually landed by re-reading persisted state through a
        // fresh connection/transaction. Usuario has RLS and this test has no ambient
        // tenant context, so the re-read goes through the same pre-tenant-context
        // bypass path the production code itself uses for this exact situation.
        Usuario reloaded = authService.findUsuarioByIdBypassingTenant(usuarioId[0]);
        assertThat(reloaded).isNotNull();
        assertThat(passwordEncoder.matches(senhaNova, reloaded.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches(senhaAntiga, reloaded.getSenhaHash())).isFalse();

        PasswordResetToken reloadedToken = tokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        assertThat(reloadedToken.getUsadoEm()).isNotNull();
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
