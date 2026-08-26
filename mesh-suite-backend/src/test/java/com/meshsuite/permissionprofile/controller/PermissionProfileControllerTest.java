package com.meshsuite.permissionprofile.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PermissionProfileControllerTest extends AbstractIntegrationTest {

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

    private String profilePayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Perfil de teste",
                  "grants": [{"module": "CUSTOMER", "action": "VIEW"}]
                }
                """.formatted(name);
    }

    @Test
    void createsListsUpdatesAndDeletesProfile() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Financeiro"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/permission-profiles").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)); // 4 seeded defaults + Financeiro

        mockMvc.perform(put("/api/permission-profiles/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Financeiro Atualizado",
                                  "description": "",
                                  "grants": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Financeiro Atualizado"));

        mockMvc.perform(delete("/api/permission-profiles/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/permission-profiles/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletingASystemProfileWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String listBody = mockMvc.perform(get("/api/permission-profiles").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> adminIds = com.jayway.jsonpath.JsonPath.read(listBody, "$.content[?(@.name=='Admin')].id");
        String adminId = adminIds.get(0);

        mockMvc.perform(delete("/api/permission-profiles/" + adminId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/permission-profiles").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload("Financeiro")))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/permission-profiles"))
                .andExpect(status().isUnauthorized());
    }
}
