package com.meshsuite.paymentmethod.controller;

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
class PaymentMethodControllerTest extends AbstractIntegrationTest {

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
        user.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.DELETE));
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String loginWithoutPayableViewPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    private String parcelado3xPayload(String description) {
        return """
                {
                  "description": "%s",
                  "active": true,
                  "installments": [
                    {"daysDue": 30, "percentage": 34.00},
                    {"daysDue": 60, "percentage": 33.00},
                    {"daysDue": 90, "percentage": 33.00}
                  ]
                }
                """.formatted(description);
    }

    @Test
    void createsListsUpdatesAndDeletesPaymentMethod() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/payment-methods").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelado3xPayload("30/60/90")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("30/60/90"))
                .andExpect(jsonPath("$.installments.length()").value(3))
                .andExpect(jsonPath("$.installments[0].installmentNumber").value(1))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/payment-methods").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("30/60/90"))
                .andExpect(jsonPath("$.content[0].installmentsCount").value(3));

        mockMvc.perform(put("/api/payment-methods/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "30/60/90 Atualizado",
                                  "active": true,
                                  "installments": [{"daysDue": 0, "percentage": 100.00}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("30/60/90 Atualizado"))
                .andExpect(jsonPath("$.installments.length()").value(1));

        mockMvc.perform(delete("/api/payment-methods/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/payment-methods/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateDescriptionWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/payment-methods").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelado3xPayload("30/60/90")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payment-methods").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelado3xPayload("30/60/90")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInstallmentsNotSummingTo100WithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/payment-methods").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Errado",
                                  "active": true,
                                  "installments": [{"daysDue": 30, "percentage": 40.00}, {"daysDue": 60, "percentage": 40.00}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPaymentMethod() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/payment-methods").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelado3xPayload("30/60/90")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        entityManager.clear();

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        mockMvc.perform(get("/api/payment-methods/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/payment-methods"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutPayableViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutPayableViewPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/payment-methods").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
