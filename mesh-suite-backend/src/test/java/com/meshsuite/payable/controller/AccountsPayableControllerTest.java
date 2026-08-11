package com.meshsuite.payable.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
import com.meshsuite.payable.service.AccountsPayableService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AccountsPayableControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired AccountsPayableService accountsPayableService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, UUID tenantId, UUID supplierId) {
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
        userLogin.setName("Carlos");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        userRepository.saveAndFlush(userLogin);

        Partner fornecedor = new Partner();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setPersonType(PersonType.LEGAL_ENTITY);
        fornecedor.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        fornecedor.setTradeName("Tecidos Aurora");
        fornecedor.getRoles().add(PartnerRole.SUPPLIER);
        partnerRepository.saveAndFlush(fornecedor);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, tenant.getId(), fornecedor.getId());
    }

    @Test
    void listsAndUpdatesStatusOfAccountsPayable() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        var created = accountsPayableService.createInstallments(ctx.tenantId(), ctx.supplierId(), null,
                List.of(new AccountsPayableInstallmentInput(new BigDecimal("50.00"), LocalDate.now().plusDays(30))));
        String id = created.get(0).id().toString();

        mockMvc.perform(get("/api/accounts-payable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].amount").value(50.00))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void tenantACannotUpdateTenantBsAccountsPayableStatus() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora", "carlos@aurora.com.br", "11222333000144");

        var created = accountsPayableService.createInstallments(ctxA.tenantId(), ctxA.supplierId(), null,
                List.of(new AccountsPayableInstallmentInput(new BigDecimal("50.00"), LocalDate.now().plusDays(30))));
        String id = created.get(0).id().toString();

        Contexto ctxB = loginAndSetUp("boreal", "marina@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(patch("/api/accounts-payable/" + id + "/status").cookie(cookieB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts-payable"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutPayableViewPermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setLegalName("sem-permissao Ltda");
        company.setCnpj("11222333000144");
        companyRepository.saveAndFlush(company);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/accounts-payable").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
