package com.meshsuite.purchaseorder.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PurchaseOrderControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String supplierId, String buyerId, String productId) {
    }

    private Contexto loginAndSetUp(String codigo, String email, String companyCnpj) throws Exception {
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
        userLogin.setName("Carlos Comprador");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMINISTRATIVE);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.DELETE));
        User savedBuyer = userRepository.saveAndFlush(userLogin);

        Partner supplier = new Partner();
        supplier.setTenantId(tenant.getId());
        supplier.setPersonType(PersonType.LEGAL_ENTITY);
        supplier.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        supplier.setTradeName("Tecidos Aurora");
        supplier.getRoles().add(PartnerRole.SUPPLIER);
        partnerRepository.saveAndFlush(supplier);

        Product produto = new Product();
        produto.setTenantId(tenant.getId());
        produto.setName("Tecido Algodão");
        produto.setSku("P0001-" + codigo);
        produto.setSalePrice(new BigDecimal("25.00"));
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, supplier.getId().toString(), savedBuyer.getId().toString(), produto.getId().toString());
    }

    private String purchaseOrderPayload(Contexto ctx) {
        return """
                {
                  "supplierId": "%s",
                  "buyerId": "%s",
                  "discount": 0,
                  "items": [
                    { "productId": "%s", "quantity": 2, "unitPrice": 100.00 }
                  ]
                }
                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId());
    }

    @Test
    void createsListsUpdatesChangesStatusAndDeletesPurchaseOrder() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/purchase-orders").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(put("/api/purchase-orders/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 20.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2, "unitPrice": 100.00 }
                                  ]
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(180.00));

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/purchase-orders/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/api/purchase-orders/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/purchase-orders/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItemsWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": []
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeDiscountWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": -10.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2, "unitPrice": 100.00 }
                                  ]
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSupplierWithoutFornecedorPapelWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 1, "unitPrice": 10.00 } ]
                                }
                                """.formatted(ctx.buyerId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPurchaseOrder() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/purchase-orders").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal", "marina@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/purchase-orders/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/purchase-orders"))
                .andExpect(status().isUnauthorized());
    }

    private String loginWithoutPurchasePermission(String codigo, String email, String companyCnpj) throws Exception {
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
    void listingWithoutPurchaseViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutPurchasePermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/purchase-orders").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void buyersEndpointReturnsRealContentAndWorksWithoutPurchasePermission() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(get("/api/users/buyers").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Carlos Comprador"));
    }
}
