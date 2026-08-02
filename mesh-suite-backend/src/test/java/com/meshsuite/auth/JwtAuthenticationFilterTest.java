package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

class JwtAuthenticationFilterTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired EntityManager entityManager;

    @Test
    void rejectsRequestWithNoCookie() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void rejectsRequestForDeactivatedUser() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        // Fixture insert needs app.tenant_id set (usuario_tenant_isolation gates INSERT
        // too); RESET afterward so the request under test starts from a clean, no-context
        // state -- otherwise a broken filter could spuriously pass on leftover context.
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.setActive(false);
        userRepository.saveAndFlush(user);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String token = jwtService.generateToken(user.getId(), tenant.getId(), java.util.UUID.randomUUID(), "ADMIN", false);

        // .header(HttpHeaders.COOKIE, ...) does NOT populate request.getCookies() in
        // MockMvc -- only the .cookie(...) builder method does. Using .header() here
        // would make the filter see no cookie at all and this test would pass for the
        // wrong reason (indistinguishable from rejectsRequestWithNoCookie above).
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(JwtAuthenticationFilter.COOKIE_NAME, token)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
