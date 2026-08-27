package com.meshsuite.category.controller;

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
class CategoryControllerTest extends AbstractIntegrationTest {

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
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String loginWithoutProductPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    private String categoryPayload(String name) {
        return """
                {
                  "name": "%s"
                }
                """.formatted(name);
    }

    private String categoryPayload(String name, String parentId) {
        return """
                {
                  "name": "%s",
                  "parentId": "%s"
                }
                """.formatted(name, parentId);
    }

    @Test
    void createsListsUpdatesAndDeletesCategory() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camisas"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Camisas"));

        mockMvc.perform(put("/api/categories/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas Atualizadas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camisas Atualizadas"));

        mockMvc.perform(delete("/api/categories/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDeletingACategoryInUseWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String categoryId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo",
                                  "sku": "P0001",
                                  "categoryId": "%s",
                                  "salePrice": 59.90,
                                  "stockQuantity": 10,
                                  "measurementUnit": "UN"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/" + categoryId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignsAParentCategoryAndRejectsANonRootParent() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String higiene = mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Higiene Pessoal")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String higieneId = com.jayway.jsonpath.JsonPath.read(higiene, "$.id");

        String beleza = mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Beleza", higieneId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(higieneId))
                .andExpect(jsonPath("$.parentName").value("Higiene Pessoal"))
                .andReturn().getResponse().getContentAsString();
        String belezaId = com.jayway.jsonpath.JsonPath.read(beleza, "$.id");

        mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Maquiagem", belezaId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/categories").cookie(cookie).param("raiz", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Higiene Pessoal"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void reportsTotalActiveAndInactiveCounts() throws Exception {
        String token = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/categories").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories/counts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.inactive").value(0));
    }

    @Test
    void tenantACannotAccessTenantBsCategory() throws Exception {
        String tokenA = loginAndGetCookie("aurora-cat", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/categories").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload("Camisas")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal-cat", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/categories/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-cat", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/categories").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
