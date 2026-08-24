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
class ProdutoKitControllerTest extends AbstractIntegrationTest {

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
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    private String produtoSimplesPayload(String sku) {
        return """
                {
                  "nome": "Camiseta Polo Masculina",
                  "sku": "%s",
                  "precoVenda": 89.90,
                  "quantidadeEstoque": 10,
                  "unidadeMedida": "UN"
                }
                """.formatted(sku);
    }

    @Test
    void createsAndFetchesKit() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String produto = mockMvc.perform(post("/api/produtos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoSimplesPayload("P0001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String produtoId = com.jayway.jsonpath.JsonPath.read(produto, "$.id");

        String kitPayload = """
                {
                  "nome": "Kit Look Casual",
                  "sku": "KIT001",
                  "unidadeMedida": "UN",
                  "itens": [{ "produtoId": "%s", "quantidade": 2 }]
                }
                """.formatted(produtoId);

        String created = mockMvc.perform(post("/api/produtos/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kitPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Kit Look Casual"))
                .andExpect(jsonPath("$.precoVenda").value(179.80))
                .andReturn().getResponse().getContentAsString();
        String kitId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/produtos/kits/" + kitId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].quantidade").value(2));
    }

    @Test
    void rejectsKitWithNoItems() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/produtos/kits").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Kit Vazio", "sku": "KIT001", "itens": [] }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/produtos/kits/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
