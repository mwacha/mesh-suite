package com.meshsuite.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.service.RateLimiter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
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

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode(senhaPlano));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
    }

    // Generic version of seedTenantWithUsuario, for the multi-account tests below
    // where the same e-mail needs to exist under more than one tenant.
    private Tenant seedTenantWithUser(String tenantCodigo, String email, String senhaPlano, String cnpj) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(tenantCodigo);
        tenant.setNome(tenantCodigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(tenantCodigo + " Ltda");
        company.setCnpj(cnpj);
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName(tenantCodigo);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(senhaPlano));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
        return tenant;
    }

    private void seedTenantWithInactiveUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora-inactive-usuario");
        tenant.setNome("Aurora Inactive Usuario");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Aurora Ltda");
        company.setCnpj("11222333000155");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Inativa");
        user.setEmail("inativa@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode(senhaPlano));
        user.setRole(Role.ADMIN);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
    }

    private void seedInactiveTenantWithUsuario(String senhaPlano) {
        Tenant tenant = new Tenant();
        tenant.setCodigo("boreal-inactive-tenant");
        tenant.setNome("Boreal Inactive Tenant");
        tenant.setAtivo(false);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("Boreal Ltda");
        company.setCnpj("55666777000155");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Carlos");
        user.setEmail("carlos@boreal.com.br");
        user.setPasswordHash(passwordEncoder.encode(senhaPlano));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

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
                .andExpect(jsonPath("$.papel").value("ADMIN"))
                .andExpect(jsonPath("$.nomeEmpresa").value("Aurora Ltda"));
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

    @Test
    void loginWithDifferentPasswordsPerTenantSkipsThePickerAndLogsStraightIn() throws Exception {
        // Same e-mail, different accounts/passwords -- the password alone
        // disambiguates which tenant, so no selection step should be needed.
        seedTenantWithUser("aurora-multi-a", "marcus@aurora.com.br", "senhaAurora123", "11222333000144");
        seedTenantWithUser("linda-brasil-multi-a", "marcus@aurora.com.br", "senhaLinda123", "22333444000155");

        String body = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marcus@aurora.com.br","senha":"senhaLinda123","manterConectado":false}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("\"contas\":[]");
    }

    @Test
    void loginWithSameEmailAndPasswordAcrossTenantsReturnsAccountsForSelection() throws Exception {
        seedTenantWithUser("aurora-multi-b", "marcus@boreal.com.br", "senhaCompartilhada", "11222333000166");
        Tenant lindaBrasil = seedTenantWithUser("linda-brasil-multi-b", "marcus@boreal.com.br", "senhaCompartilhada", "22333444000177");

        var response = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.4"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marcus@boreal.com.br","senha":"senhaCompartilhada","manterConectado":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contas.length()").value(2))
                .andReturn().getResponse();

        String cookieHeader = response.getHeader("Set-Cookie");
        org.assertj.core.api.Assertions.assertThat(cookieHeader).contains("mesh_pending_selection=");
        org.assertj.core.api.Assertions.assertThat(cookieHeader).doesNotContain("mesh_token=");

        String pendingToken = cookieHeader.split("mesh_pending_selection=")[1].split(";")[0];

        String selectBody = mockMvc.perform(post("/api/auth/select-account")
                        .cookie(new jakarta.servlet.http.Cookie("mesh_pending_selection", pendingToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + lindaBrasil.getId() + "\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        org.assertj.core.api.Assertions.assertThat(selectBody).contains("mesh_token=");
        String sessionToken = selectBody.split("mesh_token=")[1].split(";")[0];

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(JwtAuthenticationFilter.COOKIE_NAME, sessionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("linda-brasil-multi-b"));
    }

    @Test
    void selectAccountRejectsATenantIdNotAmongTheValidatedOptions() throws Exception {
        seedTenantWithUser("aurora-multi-c", "marcus@sul.com.br", "senhaCompartilhada", "11222333000188");
        seedTenantWithUser("linda-brasil-multi-c", "marcus@sul.com.br", "senhaCompartilhada", "22333444000199");
        Tenant outroTenant = seedTenantWithUser("outro-tenant-multi-c", "alguem@outro.com.br", "outrasenha", "33444555000100");

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("10.10.0.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marcus@sul.com.br","senha":"senhaCompartilhada","manterConectado":false}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String pendingToken = cookieHeader.split("mesh_pending_selection=")[1].split(";")[0];

        mockMvc.perform(post("/api/auth/select-account")
                        .cookie(new jakarta.servlet.http.Cookie("mesh_pending_selection", pendingToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + outroTenant.getId() + "\",\"manterConectado\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selectAccountWithoutPendingCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/select-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + UUID.randomUUID() + "\",\"manterConectado\":false}"))
                .andExpect(status().isUnauthorized());
    }
}
