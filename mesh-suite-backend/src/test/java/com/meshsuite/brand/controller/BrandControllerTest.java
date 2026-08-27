package com.meshsuite.brand.controller;

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
class BrandControllerTest extends AbstractIntegrationTest {

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

    private String brandPayload(String name) {
        return """
                {
                  "name": "%s"
                }
                """.formatted(name);
    }

    @Test
    void createsListsUpdatesAndDeletesBrand() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/brands").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Marca Alpha"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/brands").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Marca Alpha"));

        mockMvc.perform(get("/api/brands/counts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.inactive").value(0));

        mockMvc.perform(put("/api/brands/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha Atualizada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Marca Alpha Atualizada"));

        mockMvc.perform(delete("/api/brands/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/brands/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/brands").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/brands").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingNameWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/brands").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDeletingABrandInUseWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/brands").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String brandId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/products").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo",
                                  "sku": "P0001",
                                  "brandId": "%s",
                                  "salePrice": 59.90,
                                  "stockQuantity": 10,
                                  "measurementUnit": "UN"
                                }
                                """.formatted(brandId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/brands/" + brandId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsBrand() throws Exception {
        String tokenA = loginAndGetCookie("aurora-corest", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/brands").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandPayload("Marca Alpha")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal-corest", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/brands/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-br", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/brands").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
