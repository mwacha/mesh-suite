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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @Transactional here wraps the whole test method (fixture inserts AND the mockMvc
// calls) in one connection/transaction, rolled back after — Spring's MockMvc runs
// in-process on the same thread, so it joins this transaction rather than opening
// its own. That's what lets seedTenantWithUsuario set app.tenant_id for its own
// inserts (RLS gates INSERT too, see Task 3/4) and then RESET it before the actual
// request, so the login flow is exercised from a genuinely clean, no-context state.
@Transactional
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    // RateLimiter (Task 9) is an in-memory singleton bean shared across every test
    // class in this run's cached Spring context, keyed partly by caller IP. MockMvc
    // defaults every request's remote address to the same "127.0.0.1", so failed
    // logins from unrelated tests would otherwise accumulate against that one IP
    // bucket and eventually 429-block genuinely unrelated requests. The two original
    // tests above intentionally use the default remote address (their combined 2
    // failures never approach the 5-attempt threshold), but the additional
    // generic-error tests below each perform two failing logins; giving each its own
    // synthetic IP keeps them from ever contributing to (or being blocked by) any
    // other test's rate-limit state.
    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void seedTenantWithUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Aurora Ltda");
        empresa.setCnpj("11222333000144");
        empresaRepository.saveAndFlush(empresa);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail("marina@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode(senhaPlano));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
    }

    private void seedTenantWithInactiveUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-inactive-usuario");
        tenant.setNome("Aurora Inactive Usuario");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Aurora Ltda");
        empresa.setCnpj("11222333000155");
        empresaRepository.saveAndFlush(empresa);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Inativa");
        usuario.setEmail("inativa@aurora.com.br");
        usuario.setSenhaHash(passwordEncoder.encode(senhaPlano));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
    }

    private void seedInactiveTenantWithUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("boreal-inactive-tenant");
        tenant.setNome("Boreal Inactive Tenant");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("Boreal Ltda");
        empresa.setCnpj("55666777000155");
        empresaRepository.saveAndFlush(empresa);

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Carlos");
        usuario.setEmail("carlos@boreal.com.br");
        usuario.setSenhaHash(passwordEncoder.encode(senhaPlano));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
    }

    @Test
    void validLoginSetsCookieAndAllowsMe() throws Exception {
        seedTenantWithUsuario("senha123");

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br","senha":"senha123","manterConectado":false}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        org.assertj.core.api.Assertions.assertThat(cookieHeader).contains("mesh_token=");
        org.assertj.core.api.Assertions.assertThat(cookieHeader).contains("HttpOnly");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(JwtAuthenticationFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Marina"))
                .andExpect(jsonPath("$.papel").value("ADMINISTRADOR"));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnIdenticalGenericError() throws Exception {
        seedTenantWithUsuario("senha123");

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br","senha":"errada","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@aurora.com.br","senha":"qualquer","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(wrongPasswordBody).isEqualTo(unknownEmailBody);
    }

    @Test
    void inactiveUsuarioReturnsIdenticalGenericError() throws Exception {
        seedTenantWithInactiveUsuario("senha123");

        String baselineBody = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@aurora.com.br","senha":"qualquer","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String inactiveUsuarioBody = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inativa@aurora.com.br","senha":"senha123","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(inactiveUsuarioBody).isEqualTo(baselineBody);
    }

    @Test
    void inactiveTenantReturnsIdenticalGenericError() throws Exception {
        seedInactiveTenantWithUsuario("senha123");

        String baselineBody = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@boreal.com.br","senha":"qualquer","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String inactiveTenantBody = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos@boreal.com.br","senha":"senha123","manterConectado":false}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(inactiveTenantBody).isEqualTo(baselineBody);
    }
}
