package com.meshsuite.salesorder.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class SalesOrderControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Context(String cookie, String customerId, String salespersonId, String productId) {
    }

    private Context loginAndSetUp(String codigo, String email, String companyCnpj) throws Exception {
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

        User userLogin = new User();
        userLogin.setTenantId(tenant.getId());
        userLogin.setName("Marina");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
        userRepository.saveAndFlush(userLogin);

        User salesperson = new User();
        salesperson.setTenantId(tenant.getId());
        salesperson.setName("Carla Vendedora");
        salesperson.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        salesperson.setPasswordHash("hash");
        salesperson.setRole(Role.SALES_REP);
        salesperson.setProfile(Profile.SALES);
        userRepository.saveAndFlush(salesperson);

        Partner customer = new Partner();
        customer.setTenantId(tenant.getId());
        customer.setPersonType(PersonType.LEGAL_ENTITY);
        customer.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        customer.setTradeName("Mercado Silva");
        customer.getRoles().add(PartnerRole.CUSTOMER);
        partnerRepository.saveAndFlush(customer);

        Product product = new Product();
        product.setTenantId(tenant.getId());
        product.setName("Camiseta Polo");
        product.setSku("P0001-" + codigo);
        product.setSalePrice(new BigDecimal("59.90"));
        productRepository.saveAndFlush(product);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Context(token, customer.getId().toString(), salesperson.getId().toString(), product.getId().toString());
    }

    private String salesOrderPayload(Context ctx) {
        return """
                {
                  "customerId": "%s",
                  "salespersonId": "%s",
                  "discount": 0,
                  "items": [
                    { "productId": "%s", "quantity": 2, "unitPrice": 59.90 }
                  ]
                }
                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId());
    }

    @Test
    void createsListsUpdatesAdvancesAndDeletesSalesOrder() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/sales-orders").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(put("/api/sales-orders/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 10.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2, "unitPrice": 59.90 }
                                  ]
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(109.80));

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"));

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/sales-orders/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sales-orders/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItemsWithBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 0,
                                  "items": []
                                }
                                """.formatted(ctx.customerId(), ctx.salespersonId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCustomerWithoutCustomerRoleWithBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "salespersonId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 1, "unitPrice": 10.00 } ]
                                }
                                """.formatted(ctx.salespersonId(), ctx.salespersonId(), ctx.productId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsSalesOrder() throws Exception {
        Context ctxA = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/sales-orders").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Context ctxB = loginAndSetUp("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/sales-orders/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/sales-orders"))
                .andExpect(status().isUnauthorized());
    }

    private String loginWithoutOrderPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    @Test
    void listingWithoutOrderViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutOrderPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/sales-orders").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void advancingToInvoicedViaStatusEndpointIsRejected() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sales-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVOICED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsMonthlyRevenueAndOrdersByPeriod() throws Exception {
        Context ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/sales-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salesOrderPayload(ctx)))
                .andExpect(status().isCreated());

        // The order stays DRAFT (never invoiced via this flow -- see
        // advancingToInvoicedViaStatusEndpointIsRejected), so it must not count.
        mockMvc.perform(get("/api/sales-orders/monthly-revenue").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMonthRevenue").value(0));

        String currentMonthBody = mockMvc.perform(get("/api/sales-orders/orders-by-period").cookie(cookie)
                        .param("period", "CURRENT_MONTH"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> currentMonthCounts = com.jayway.jsonpath.JsonPath.read(currentMonthBody, "$[*].count");
        assertThat(currentMonthCounts.get(currentMonthCounts.size() - 1)).isEqualTo(1);

        mockMvc.perform(get("/api/sales-orders/orders-by-period").cookie(cookie)
                        .param("period", "LAST_12_MONTHS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));
    }
}
