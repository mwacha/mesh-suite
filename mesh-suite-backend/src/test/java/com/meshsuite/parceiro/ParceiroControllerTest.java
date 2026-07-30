package com.meshsuite.parceiro;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.JwtAuthenticationFilter;
import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
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
class ParceiroControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UsuarioRepository usuarioRepository;
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

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome("Marina");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha123"));
        usuario.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(usuario);

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
                  "tipoPessoa": "JURIDICA",
                  "documento": "%s",
                  "nomeFantasia": "Mercado Silva",
                  "razaoSocial": "Mercado Silva Ltda",
                  "papeis": ["CLIENTE"],
                  "cidade": "São Paulo",
                  "uf": "SP",
                  "contatos": []
                }
                """.formatted(documento);
    }

    @Test
    void createsListsUpdatesAndDeletesParceiro() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String created = mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeFantasia").value("Mercado Silva"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/parceiros").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nomeFantasia").value("Mercado Silva"));

        mockMvc.perform(put("/api/parceiros/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoPessoa": "JURIDICA",
                                  "documento": "22333444000155",
                                  "nomeFantasia": "Mercado Silva Atualizado",
                                  "papeis": ["CLIENTE","FORNECEDOR"],
                                  "contatos": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeFantasia").value("Mercado Silva Atualizado"))
                .andExpect(jsonPath("$.papeis.length()").value(2));

        mockMvc.perform(patch("/api/parceiros/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOQUEADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"));

        mockMvc.perform(delete("/api/parceiros/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/parceiros/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateDocumentoWithConflict() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsStatusUpdateToEmRisco() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        String body = mockMvc.perform(post("/api/parceiros").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/parceiros/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_RISCO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsParceiro() throws Exception {
        String tokenA = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenA);

        String body = mockMvc.perform(post("/api/parceiros").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parceiroPayload("22333444000155")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // This whole test method runs inside one Spring-test-managed transaction, so
        // the ParceiroService.criar() call above and buscarPorId() below (via the GET
        // as tenant B) share a single Hibernate persistence context. Without this
        // clear(), findById() would return the already-managed Parceiro instance from
        // the first-level cache without issuing SQL at all, bypassing RLS entirely and
        // leaking tenant A's row to tenant B -- a test-harness artifact of sharing one
        // EntityManager across "requests" here, not a real production behavior (each
        // production HTTP request gets its own EntityManager). Same pattern used in
        // ParceiroRepositoryTest/EmpresaRepositoryTest/UsuarioRepositoryTest.
        entityManager.clear();

        String tokenB = loginAndGetCookie("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, tokenB);

        mockMvc.perform(get("/api/parceiros/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/parceiros"))
                .andExpect(status().isUnauthorized());
    }
}
