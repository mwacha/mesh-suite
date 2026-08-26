package com.meshsuite.product.controller;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VariationProductControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record LoginContext(String cookie, UUID tenantId) {
    }

    private LoginContext loginAndSetUp(String code, String email, String companyCnpj) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(code);
        tenant.setNome(code);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName(code + " Ltda");
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

        return new LoginContext(cookieHeader.split("mesh_token=")[1].split(";")[0], tenant.getId());
    }

    private String variationPayload(String sku) {
        return """
                {
                  "name": "Camiseta Polo",
                  "sku": "%s",
                  "brand": "Marca Alpha",
                  "salePrice": 89.90,
                  "children": [
                    { "sku": "%s-P", "salePrice": 79.90, "size": "P" },
                    { "sku": "%s-M", "salePrice": 84.90, "size": "M" }
                  ]
                }
                """.formatted(sku, sku, sku);
    }

    @Test
    void createsListsAndUpdatesAVariation() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-var", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/products/variations").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variationPayload("V0001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camiseta Polo"))
                .andExpect(jsonPath("$.children.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String firstChildId = com.jayway.jsonpath.JsonPath.read(created, "$.children[0].id");

        mockMvc.perform(get("/api/products/variations/" + id).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].sku").value("V0001-P"));

        // The Simples listing must never show a Variação parent or its children.
        mockMvc.perform(get("/api/products").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(put("/api/products/variations/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camiseta Polo",
                                  "sku": "V0001",
                                  "brand": "Marca Alpha",
                                  "salePrice": 89.90,
                                  "children": [
                                    { "id": "%s", "sku": "V0001-P", "salePrice": 82.90, "size": "P" },
                                    { "sku": "V0001-G", "salePrice": 89.90, "size": "G" }
                                  ]
                                }
                                """.formatted(firstChildId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children.length()").value(2))
                .andExpect(jsonPath("$.children[*].sku", org.hamcrest.Matchers.containsInAnyOrder("V0001-P", "V0001-G")));
    }

    @Test
    void sharedStatusAndDeleteEndpointsDispatchToAVariationParent() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-var", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/products/variations").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variationPayload("V0001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String childId = com.jayway.jsonpath.JsonPath.read(created, "$.children[0].id");

        // The child created above stays managed in this test's single Hibernate
        // session; deleting the parent later would make Hibernate process that
        // unrelated-but-resident entity during flush, which throws spuriously (see
        // the same clear() in VariationProductServiceTest.deletingTheParentCascades...).
        // A real request has no such carryover.
        entityManager.clear();

        // ProductTypeStrategyResolver looks the id's real type up and dispatches here --
        // before Phase 4 this 404'd, since /api/products/{id} only resolved type=PRODUCT.
        mockMvc.perform(patch("/api/products/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNoContent());

        // VARIATION_CHILD has no strategy of its own -- only ever managed through its
        // parent's update() -- so the shared endpoint reports it as not found.
        mockMvc.perform(delete("/api/products/" + childId).cookie(cookie))
                .andExpect(status().isNotFound());

        // The lookup above re-loaded the child into this session -- clear() again so
        // deleting the parent next doesn't hit the same spurious flush issue.
        entityManager.clear();

        mockMvc.perform(delete("/api/products/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/variations/" + id).cookie(cookie))
                .andExpect(status().isNotFound());

        // The DB-level ON DELETE CASCADE on parent_product_id removed the child too.
        assertThatChildIsGone(childId);
    }

    private void assertThatChildIsGone(String childId) {
        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM product WHERE id = '" + childId + "'")
                .getSingleResult()).longValue();
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    @Test
    void rejectsEmptyChildrenWithBadRequest() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-var", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/products/variations").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Sem Variantes", "sku": "V0001", "salePrice": 10.00, "children": [] }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsVariation() throws Exception {
        LoginContext ctxA = loginAndSetUp("aurora-var", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/products/variations").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variationPayload("V0001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        LoginContext ctxB = loginAndSetUp("boreal-var", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        entityManager.clear();

        mockMvc.perform(get("/api/products/variations/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }
}
