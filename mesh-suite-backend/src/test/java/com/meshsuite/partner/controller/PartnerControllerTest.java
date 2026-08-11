package com.meshsuite.partner.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.service.PartnerService;
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
class PartnerControllerTest extends AbstractIntegrationTest {

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
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.DELETE));
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String loginWithoutCustomerPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    private String parceiroPayload(String documento) {
        return """
                {
                  "personType": "LEGAL_ENTITY",
                  "document": "%s",
                  "tradeName": "Mercado Silva",
                  "legalName": "Mercado Silva Ltda",
                  "roles": ["CUSTOMER"],
                  "city": "São Paulo",
                  "state": "SP",
                  "contacts": []
                }
                """.formatted(documento);
    }

    @Test
    void createsListsUpdatesAndDeletesPartner() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeName").value("Mercado Silva"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/partners").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tradeName").value("Mercado Silva"));

        mockMvc.perform(put("/api/partners/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personType": "LEGAL_ENTITY",
                                  "document": "22333444000155",
                                  "tradeName": "Mercado Silva Atualizado",
                                  "roles": ["CUSTOMER","SUPPLIER"],
                                  "contacts": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Mercado Silva Atualizado"))
                .andExpect(jsonPath("$.roles.length()").value(2));

        mockMvc.perform(patch("/api/partners/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mockMvc.perform(delete("/api/partners/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/partners/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsWithDocumentFilterIgnoringMaskAndMatchingPartially() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/partners").cookie(cookie).param("documento", "223.334.440/0015-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/partners").cookie(cookie).param("documento", "33444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/partners").cookie(cookie).param("documento", "00000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listsWithMultiValueStatusFilter() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated());

        String bloqueadoBody = mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("33444555000166")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bloqueadoId = com.jayway.jsonpath.JsonPath.read(bloqueadoBody, "$.id");
        mockMvc.perform(patch("/api/partners/" + bloqueadoId + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/partners").cookie(cookie).param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/partners").cookie(cookie).param("status", "ACTIVE", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void rejectsDuplicateDocumentWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsStatusUpdateToAtRisk() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String body = mockMvc.perform(post("/api/partners").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/partners/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AT_RISK\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPartner() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/partners").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // This whole test method runs inside one Spring-test-managed transaction, so
        // the PartnerService.create() call above and findById() below (via the GET
        // as tenant B) share a single Hibernate persistence context. Without this
        // clear(), findById() would return the already-managed Partner instance from
        // the first-level cache without issuing SQL at all, bypassing RLS entirely and
        // leaking tenant A's row to tenant B -- a test-harness artifact of sharing one
        // EntityManager across "requests" here, not a real production behavior (each
        // production HTTP request gets its own EntityManager). Same pattern used in
        // PartnerRepositoryTest/CompanyRepositoryTest/UserRepositoryTest.
        entityManager.clear();

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        mockMvc.perform(get("/api/partners/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/partners"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutCustomerViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutCustomerPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/partners").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
