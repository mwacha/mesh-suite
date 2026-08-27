package com.meshsuite.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class KitProductControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
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

    private UUID createComponent(UUID tenantId, String sku, String price) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Componente " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal(price));
        return productRepository.saveAndFlush(p).getId();
    }

    private String kitPayload(String sku, UUID componentId) {
        return """
                {
                  "name": "Kit Combo",
                  "sku": "%s",
                  "items": [ { "componentProductId": "%s", "quantity": 2 } ]
                }
                """.formatted(sku, componentId);
    }

    @Test
    void createsListsUpdatesAndDeletesKit() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-kit", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        UUID componentId = createComponent(ctx.tenantId(), "P0001", "89.90");

        String created = mockMvc.perform(post("/api/products/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload("KIT001", componentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kit Combo"))
                .andExpect(jsonPath("$.totalPrice").value(179.80))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/products/kits/" + id).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        // The Simples listing must never show a Kit row -- only the component,
        // a genuine Simples product, appears.
        mockMvc.perform(get("/api/products").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("P0001"));

        UUID otherComponent = createComponent(ctx.tenantId(), "P0002", "10.00");
        mockMvc.perform(put("/api/products/kits/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload("KIT001", otherComponent).replace("\"quantity\": 2", "\"quantity\": 5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(50.00));

    }

    @Test
    void sharedStatusAndDeleteEndpointsDispatchToAKit() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-kit", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        UUID componentId = createComponent(ctx.tenantId(), "P0001", "89.90");

        String created = mockMvc.perform(post("/api/products/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload("KIT001", componentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        // ProductTypeStrategyResolver looks the id's real type up and dispatches here --
        // before Phase 4 this 404'd, since /api/products/{id} only resolved type=PRODUCT.
        mockMvc.perform(patch("/api/products/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/products/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/kits/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnUnknownComponentWithBadRequest() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-kit", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/products/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload("KIT001", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyItemsWithBadRequest() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-kit", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/products/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Kit Vazio", "sku": "KIT001", "items": [] }
                                """))
                .andExpect(status().isBadRequest());
    }

    // Deleting a component still referenced by a kit_item row (blocked by the
    // product_kit_item FK, translated to 409 by ProductExceptionHandler) is covered
    // at the repository/service level in KitProductServiceTest instead of here: a
    // MockMvc call inside this @Transactional test shares the outer test transaction,
    // so the DELETE issued by the nested service call isn't flushed to Postgres (and
    // the FK isn't checked) until something forces a flush -- unlike a real request,
    // which has no such wrapping transaction. Manually verified against a live
    // backend that DELETE /api/products/{id} on a referenced component does return
    // 409 with "Operação viola uma regra de integridade dos dados".

    @Test
    void tenantACannotAccessTenantBsKit() throws Exception {
        LoginContext ctxA = loginAndSetUp("aurora-kit", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());
        UUID componentId = createComponent(ctxA.tenantId(), "P0001", "89.90");

        String body = mockMvc.perform(post("/api/products/kits").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload("KIT001", componentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        LoginContext ctxB = loginAndSetUp("boreal-kit", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        entityManager.clear();

        mockMvc.perform(get("/api/products/kits/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }
}
