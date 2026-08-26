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
class SellableProductControllerTest extends AbstractIntegrationTest {

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
    void listsSimplesKitAndVariationChildrenButNotTheVariationParent() throws Exception {
        LoginContext ctx = loginAndSetUp("aurora-sellable", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        product(ctx.tenantId(), ProductType.PRODUCT, "P0001", null);
        product(ctx.tenantId(), ProductType.PRODUCT_KIT, "KIT001", null);
        Product parent = product(ctx.tenantId(), ProductType.VARIATION_PARENT, "V0001", null);
        product(ctx.tenantId(), ProductType.VARIATION_CHILD, "V0001-P", parent);

        mockMvc.perform(get("/api/products/sellable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].sku").value(
                        org.hamcrest.Matchers.containsInAnyOrder("P0001", "KIT001", "V0001-P")))
                // The picker badges each row by type, so the discriminator must be serialized.
                .andExpect(jsonPath("$.content[*].type").value(
                        org.hamcrest.Matchers.containsInAnyOrder("PRODUCT", "PRODUCT_KIT", "VARIATION_CHILD")));
    }
}
