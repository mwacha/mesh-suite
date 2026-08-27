package com.meshsuite.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductType;
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
class ProductAllListingControllerTest extends AbstractIntegrationTest {

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
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return new LoginContext(cookieHeader.split("mesh_token=")[1].split(";")[0], tenant.getId());
    }

    private Product product(UUID tenantId, ProductType type, String sku, Product parent) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setType(type);
        p.setName("Produto " + sku);
        p.setSku(sku);
        p.setSalePrice(new BigDecimal("10.00"));
        p.setParentProduct(parent);
        return productRepository.saveAndFlush(p);
    }

    @Test
    void listsSimplesKitAndVariationParentWithNestedChildren() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-all", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        product(ctx.tenantId(), ProductType.PRODUCT, "P0001", null);
        product(ctx.tenantId(), ProductType.PRODUCT_KIT, "KIT001", null);
        Product parent = product(ctx.tenantId(), ProductType.VARIATION_PARENT, "V0001", null);
        product(ctx.tenantId(), ProductType.VARIATION_CHILD, "V0001-P", parent);

        String body = mockMvc.perform(get("/api/products/all").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> content = com.jayway.jsonpath.JsonPath.read(body, "$.content");
        var bySku = content.stream().collect(java.util.stream.Collectors.toMap(m -> m.get("sku"), m -> m));

        var parentChildren = (java.util.List<?>) bySku.get("V0001").get("children");
        assertThat(parentChildren).hasSize(1);
        assertThat(((java.util.Map<?, ?>) parentChildren.get(0)).get("sku")).isEqualTo("V0001-P");
        assertThat((java.util.List<?>) bySku.get("P0001").get("children")).isEmpty();
    }

    @Test
    void filtersByTypeQueryParam() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-all", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        product(ctx.tenantId(), ProductType.PRODUCT, "P0001", null);
        product(ctx.tenantId(), ProductType.PRODUCT_KIT, "KIT001", null);

        mockMvc.perform(get("/api/products/all").param("type", "PRODUCT_KIT").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KIT001"));
    }

    @Test
    void summaryCountsActiveAndInactiveAcrossAllTypes() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-all", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        product(ctx.tenantId(), ProductType.PRODUCT, "P0001", null);
        Product inactiveKit = product(ctx.tenantId(), ProductType.PRODUCT_KIT, "KIT001", null);
        inactiveKit.setStatus(com.meshsuite.product.domain.enums.ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveKit);

        mockMvc.perform(get("/api/products/all/resumo").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.inactive").value(1));
    }

    @Test
    void tenantACannotSeeTenantBsProductsInTheUnifiedListing() throws Exception {
        LoginContext ctxA = loginAndSetUp("aurora-all", "marina@aurora.com.br", "11222333000144");
        product(ctxA.tenantId(), ProductType.PRODUCT, "P0001", null);

        LoginContext ctxB = loginAndSetUp("boreal-all", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        entityManager.clear();

        mockMvc.perform(get("/api/products/all").cookie(cookieB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
