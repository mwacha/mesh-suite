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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class TabelaPrecoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String produtoId) {
    }

    private Contexto loginAndSetUp(String codigo, String email, String cnpjEmpresa) throws Exception {
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

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Camiseta Polo");
        produto.setSku("P0001");
        produto.setPrecoVenda(new BigDecimal("59.90"));
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, produto.getId().toString());
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

    private String tabelaPrecoPayload(String nome, String produtoId) {
        return """
                {
                  "nome": "%s",
                  "modoSelecaoProdutos": "SELECIONAR_PRODUTOS",
                  "metodoAjuste": "MANUAL",
                  "arredondamento": "NAO_ARREDONDAR",
                  "inicioVigencia": "2026-01-01",
                  "itens": [
                    { "produtoId": "%s", "precoNestaTabela": 69.90, "percentualComissao": 5.00 }
                  ]
                }
                """.formatted(nome, produtoId);
    }

    @Test
    void createsListsUpdatesAndDeletesTabelaPreco() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Varejo"))
                .andExpect(jsonPath("$.itens[0].precoNestaTabela").value(69.90))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/tabelas-preco").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Varejo"));

        mockMvc.perform(put("/api/tabelas-preco/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo Atualizado", ctx.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Varejo Atualizado"));

        mockMvc.perform(delete("/api/tabelas-preco/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tabelas-preco/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNomeWithConflict() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctx.produtoId())))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingInicioVigenciaWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/tabelas-preco").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Sem Vigência",
                                  "modoSelecaoProdutos": "SELECIONAR_PRODUTOS",
                                  "metodoAjuste": "MANUAL",
                                  "arredondamento": "NAO_ARREDONDAR",
                                  "itens": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsTabelaPreco() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora-tp", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/tabelas-preco").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tabelaPrecoPayload("Varejo", ctxA.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal-tp", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404.
        entityManager.clear();

        mockMvc.perform(get("/api/tabelas-preco/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/tabelas-preco"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingWithoutProductViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutProductPermission("sem-permissao-tp", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/tabelas-preco").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
