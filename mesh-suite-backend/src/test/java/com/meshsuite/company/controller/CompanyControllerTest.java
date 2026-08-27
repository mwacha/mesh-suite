package com.meshsuite.company.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
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
class CompanyControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(codigo + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.DELETE));
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String loginWithoutUserPermission(String codigo, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(codigo + " Ltda");
        company.setCnpj(companyCnpj);
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String companyPayload(String legalName, String cnpj) {
        return """
                {
                  "legalName": "%s",
                  "cnpj": "%s"
                }
                """.formatted(legalName, cnpj);
    }

    @Test
    void createsListsUpdatesAndDeletesCompany() throws Exception {
        String token = loginAndGetCookie("aurora-empresa-ctrl", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/companies").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyPayload("Filial Sul Ltda", "22333444000155")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalName").value("Filial Sul Ltda"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/companies").cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/counts").cookie(cookie))
                .andExpect(status().isOk())
                // the login helper's own bootstrap company + the one created above
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(2));

        mockMvc.perform(put("/api/companies/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyPayload("Filial Sul S.A.", "22333444000155")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Filial Sul S.A."));

        mockMvc.perform(patch("/api/companies/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/companies/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/companies/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateCnpjWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora-empresa-ctrl", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/companies").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyPayload("Filial Sul Ltda", "22333444000155")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyPayload("Outra Filial Ltda", "22333444000155")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingLegalNameWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-empresa-ctrl", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/companies").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDeletingTheLastCompanyWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-empresa-ctrl", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String list = mockMvc.perform(get("/api/companies").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String onlyCompanyId = com.jayway.jsonpath.JsonPath.read(list, "$.content[0].id");

        mockMvc.perform(delete("/api/companies/" + onlyCompanyId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsCompany() throws Exception {
        String tokenA = loginAndGetCookie("aurora-empresa-ctrl", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/companies").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyPayload("Filial Sul Ltda", "22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal-empresa-ctrl", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/companies/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutUserViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutUserPermission("sem-permissao-emp", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/companies").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
