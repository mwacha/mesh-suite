package com.meshsuite.usuario;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.JwtAuthenticationFilter;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UsuarioControllerTest extends AbstractIntegrationTest {

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

        Usuario admin = new Usuario();
        admin.setTenantId(tenant.getId());
        admin.setNome("Marina");
        admin.setEmail(email);
        admin.setSenhaHash(passwordEncoder.encode("senha123"));
        admin.setPapel(Papel.ADMINISTRADOR);
        usuarioRepository.saveAndFlush(admin);

        Usuario representante = new Usuario();
        representante.setTenantId(tenant.getId());
        representante.setNome("Carla Vendedora");
        representante.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        representante.setSenhaHash("hash");
        representante.setPapel(Papel.REPRESENTANTE);
        usuarioRepository.saveAndFlush(representante);

        Usuario producao = new Usuario();
        producao.setTenantId(tenant.getId());
        producao.setNome("Pedro Produção");
        producao.setEmail("pedro-" + codigo + "@" + codigo + ".com.br");
        producao.setSenhaHash("hash");
        producao.setPapel(Papel.PRODUCAO);
        usuarioRepository.saveAndFlush(producao);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        return cookieHeader.split("mesh_token=")[1].split(";")[0];
    }

    @Test
    void listsOnlyUsersWithRepresentantePapel() throws Exception {
        String token = loginAndGetCookie("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/usuarios/representantes").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Carla Vendedora"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/usuarios/representantes"))
                .andExpect(status().isUnauthorized());
    }
}
