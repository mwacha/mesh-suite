package com.meshsuite.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.controller.AuthController;
import com.meshsuite.auth.service.AuthService;
import com.meshsuite.auth.service.RateLimiter;
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Deliberately has NO @Transactional anywhere in this class. AuthControllerTest's
// class-level @Transactional gives every one of its test methods an ambient
// transaction for free -- which is exactly what let a real self-invocation bug slip
// past that test design once already: AuthService.authenticate() briefly called its
// own @Transactional steps as bare `this.method(...)`, ordinary Java self-invocation
// that bypasses Spring's AOP proxy and silently no-ops @Transactional (and therefore
// TenantContextAspect) outside of an already-active transaction. AuthControllerTest's
// own @Transactional incidentally supplied one, masking the breakage; a real HTTP
// request has no such ambient transaction (AuthController isn't @Transactional, and
// open-in-view is disabled), so every login would have failed in production despite
// AuthControllerTest passing. The fix (see AuthService's `@Lazy AuthService self`
// self-injection) routes internal calls through the real proxy instead. This test
// reproduces the no-ambient-transaction condition directly -- fixture rows are
// committed for real via an explicit TransactionTemplate, then the login call itself
// runs with zero ambient transaction -- so if a bare `this.` self-invocation is ever
// reintroduced, this test (unlike AuthControllerTest) will actually catch it.
class AuthControllerNoAmbientTransactionTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void loginSucceedsWithNoAmbientTransaction() throws Exception {
        // Unique per test run so this fixture needs no cleanup afterward and can't
        // collide with any other test class's fixture rows (e.g. the "aurora"/
        // "boreal" literals used elsewhere in this suite).
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String email = "notx-" + suffix + "@example.com.br";
        String senha = "senha123";
        String cnpj = String.format("%014d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000000L);

        Tenant tenant = new Tenant();
        tenant.setCodigo("notx-" + suffix);
        tenant.setNome("No-Tx Tenant " + suffix);
        tenantRepository.saveAndFlush(tenant);

        // A real, separately-committed transaction: this test method itself has no
        // @Transactional, so nothing here gets rolled back, and nothing pre-opens a
        // transaction ahead of the mockMvc call below -- exactly like a real request.
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

            Empresa empresa = new Empresa();
            empresa.setTenantId(tenant.getId());
            empresa.setRazaoSocial("No-Tx Empresa " + suffix);
            empresa.setCnpj(cnpj);
            empresaRepository.saveAndFlush(empresa);

            User user = new User();
            user.setTenantId(tenant.getId());
            user.setName("No-Tx User");
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(senha));
            user.setRole(Role.ADMIN);
            userRepository.saveAndFlush(user);
        });

        // RateLimiter (Task 9) is an in-memory singleton bean shared across every
        // test class in this run's cached Spring context, keyed partly by caller IP;
        // MockMvc defaults every request's remote address to the same "127.0.0.1".
        // A unique synthetic IP here keeps this test immune to rate-limit state left
        // behind by unrelated tests (and vice versa), regardless of suite run order.
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.10.0.3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\",\"manterConectado\":false}"))
                .andExpect(status().isOk());
    }
}
