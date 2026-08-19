package com.meshsuite.purchaseinvoice.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PurchaseInvoiceControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Context(String cookie, String supplierId, String buyerId, String productId) {
    }

    private Context loginAndSetUp(String code, String email, String companyCnpj) throws Exception {
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
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PURCHASE_INVOICE, Action.CREATE));
        // The login user doubles as the PurchaseOrder's buyer, same as the
        // existing PurchaseOrderControllerTest -- ADMINISTRATIVE role satisfies
        // PurchaseOrderService.findValidBuyer's role check.
        User savedBuyer = userRepository.saveAndFlush(userLogin);

        Partner supplier = new Partner();
        supplier.setTenantId(tenant.getId());
        supplier.setPersonType(PersonType.LEGAL_ENTITY);
        supplier.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        supplier.setTradeName("Tecidos Aurora");
        supplier.getRoles().add(PartnerRole.SUPPLIER);
        partnerRepository.saveAndFlush(supplier);

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Compra dentro do estado");
        registration.setCfop("1102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);

        Product product = new Product();
        product.setTenantId(tenant.getId());
        product.setName("Tecido Algodão");
        product.setSku("P0001-" + code);
        product.setSalePrice(new BigDecimal("100.00"));
        product.setFiscalRegistration(registration);
        productRepository.saveAndFlush(product);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Context(token, supplier.getId().toString(), savedBuyer.getId().toString(), product.getId().toString());
    }

    private String createOpenPurchaseOrder(Context ctx, Cookie cookie) throws Exception {
        String created = mockMvc.perform(post("/api/purchase-orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "buyerId": "%s",
                                  "discount": 0,
                                  "items": [ { "productId": "%s", "quantity": 2, "unitPrice": 100.00 } ]
                                }
                                """.formatted(ctx.supplierId(), ctx.buyerId(), ctx.productId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(created, "$.id");
    }

    private String issuePayload() {
        return """
                {
                  "invoiceNumber": "NF-1001",
                  "series": "1",
                  "model": "55",
                  "issueDate": "2026-08-10",
                  "entryDate": "2026-08-12",
                  "installments": [ { "amount": 200.00, "dueDate": "2026-09-10" } ]
                }
                """;
    }

    @Test
    void issuesListsAndFindsPurchaseInvoice() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        String created = mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.purchaseOrderId").value(orderId))
                .andExpect(jsonPath("$.total").value(200.00))
                .andReturn().getResponse().getContentAsString();
        String invoiceId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/purchase-invoices").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1));

        mockMvc.perform(get("/api/purchase-invoices/" + invoiceId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierName").value("Tecidos Aurora"));

        mockMvc.perform(get("/api/purchase-orders/" + orderId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void issuingWithMismatchedInstallmentsIsBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceNumber": "NF-1001",
                                  "series": "1",
                                  "model": "55",
                                  "issueDate": "2026-08-10",
                                  "entryDate": "2026-08-12",
                                  "installments": [ { "amount": 50.00, "dueDate": "2026-09-10" } ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuingTwiceForTheSameOrderIsBadRequest() throws Exception {
        Context ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String orderId = createOpenPurchaseOrder(ctx, cookie);

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/purchase-invoices/issue/" + orderId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceNumber": "NF-1002",
                                  "series": "1",
                                  "model": "55",
                                  "issueDate": "2026-08-10",
                                  "entryDate": "2026-08-12",
                                  "installments": [ { "amount": 200.00, "dueDate": "2026-09-10" } ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issuingWithoutPurchaseInvoicePermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-compra");
        tenant.setNome("sem-permissao-compra");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("sem-permissao-compra Ltda");
        company.setCnpj("99888777000166");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao-compra@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao-compra@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/purchase-invoices").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
