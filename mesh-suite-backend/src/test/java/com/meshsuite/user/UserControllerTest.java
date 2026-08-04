package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class UserControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String cnpjEmpresa, boolean grantUserPermissions) throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial(codigo + " Ltda");
        empresa.setCnpj(cnpjEmpresa);
        empresaRepository.saveAndFlush(empresa);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMINISTRATIVE);
        user.setProfile(Profile.ADMIN);
        if (grantUserPermissions) {
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
            user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        }
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String userPayload(String email) {
        return """
                {
                  "name": "Carla Vendedora",
                  "email": "%s",
                  "phone": "(11) 98888-7777",
                  "role": "SALES_REP",
                  "profile": "SALES",
                  "active": true,
                  "password": "senha1234",
                  "confirmPassword": "senha1234",
                  "permissions": [ { "module": "ORDER", "action": "VIEW" } ]
                }
                """.formatted(email);
    }

    @Test
    void createsListsUpdatesAndTogglesStatus() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Carla Vendedora"))
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='carla@aurora.com.br')]").exists());

        mockMvc.perform(get("/api/users/counts").cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carla Vendedora Sênior",
                                  "email": "carla@aurora.com.br",
                                  "phone": "(11) 98888-7777",
                                  "role": "SALES_REP",
                                  "profile": "SALES",
                                  "active": true,
                                  "password": "",
                                  "confirmPassword": "",
                                  "permissions": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla Vendedora Sênior"));

        mockMvc.perform(patch("/api/users/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingNameWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "sem-nome@aurora.com.br",
                                  "role": "SALES_REP",
                                  "profile": "SALES",
                                  "active": true,
                                  "password": "senha1234",
                                  "confirmPassword": "senha1234",
                                  "permissions": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesCreateWhenCallerLacksUserPermission() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", false);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/users").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesRepsEndpointWorksEvenWithoutUserPermission() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", false);
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> "aurora".equals(t.getCodigo()))
                .findFirst().orElseThrow();

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        User salesRep = new User();
        salesRep.setTenantId(tenant.getId());
        salesRep.setName("Carla Vendedora");
        salesRep.setEmail("carla-vendedora@aurora.com.br");
        salesRep.setPasswordHash(passwordEncoder.encode("senha123"));
        salesRep.setRole(Role.SALES_REP);
        salesRep.setProfile(Profile.SALES);
        userRepository.saveAndFlush(salesRep);
        // Explicitly reset before the call under test -- without this, the SET
        // LOCAL above (or the earlier login's) would still be in effect for the
        // rest of this @Transactional test method regardless of whether the
        // endpoint sets its own tenant context, silently masking exactly the bug
        // this test exists to catch (see UserController.salesReps()'s comment).
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        mockMvc.perform(get("/api/users/sales-reps").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Carla Vendedora"));
    }

    @Test
    void tenantACannotAccessTenantBsUser() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144", true);
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/users").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("carla@aurora.com.br")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155", true);
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/users/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }
}
