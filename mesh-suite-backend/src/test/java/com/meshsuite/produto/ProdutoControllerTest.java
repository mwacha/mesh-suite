package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.auth.Module;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Profile;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class ProdutoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String loginAndGetCookie(String codigo, String email, String cnpjEmpresa) throws Exception {
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

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String loginWithoutProductPermission(String codigo, String email, String cnpjEmpresa) throws Exception {
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

    private String produtoPayload(String sku) {
        return """
                {
                  "nome": "Camiseta Polo Masculina",
                  "sku": "%s",
                  "precoVenda": 59.90,
                  "quantidadeEstoque": 10,
                  "unidadeMedida": "UN"
                }
                """.formatted(sku);
    }

    @Test
    void createsListsUpdatesAndDeletesProduto() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Camiseta Polo Masculina"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/produtos").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("P0001"));

        mockMvc.perform(put("/api/produtos/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Camiseta Polo Masculina Atualizada",
                                  "sku": "P0001",
                                  "precoVenda": 64.90,
                                  "quantidadeEstoque": 10,
                                  "unidadeMedida": "UN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Camiseta Polo Masculina Atualizada"));

        mockMvc.perform(patch("/api/produtos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));

        mockMvc.perform(delete("/api/produtos/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/produtos/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateSkuWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingPrecoVendaWithBadRequest() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Produto Sem Preço",
                                  "sku": "P0099"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsProduto() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/produtos").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("P0001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/produtos/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/produtos").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
