package com.meshsuite.municipio.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class MunicipioControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
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
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.ADMIN);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"marina@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    @Test
    void listsAllMunicipiosWhenNoUfIsGiven() throws Exception {
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, loginAndGetCookie());

        mockMvc.perform(get("/api/municipios").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("São Paulo")))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Rio de Janeiro")));
    }

    @Test
    void filtersMunicipiosByUf() throws Exception {
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, loginAndGetCookie());

        mockMvc.perform(get("/api/municipios").cookie(cookie).param("uf", "AC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Rio Branco")))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("São Paulo"))));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/municipios"))
                .andExpect(status().isUnauthorized());
    }
}
