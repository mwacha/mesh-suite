package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

class JwtAuthenticationFilterTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired UsuarioRepository usuarioRepository;
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

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String token = jwtService.generateToken(usuario.getId(), tenant.getId(), java.util.UUID.randomUUID(), "ADMINISTRADOR", false);

        // .header(HttpHeaders.COOKIE, ...) does NOT populate request.getCookies() in
        // MockMvc -- only the .cookie(...) builder method does. Using .header() here
        // would make the filter see no cookie at all and this test would pass for the
        // wrong reason (indistinguishable from rejectsRequestWithNoCookie above).
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(JwtAuthenticationFilter.COOKIE_NAME, token)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
