package com.meshsuite.venda.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.empresa.domain.Empresa;
import com.meshsuite.empresa.repository.EmpresaRepository;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.repository.FiscalRegistrationRepository;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.repository.ProdutoRepository;
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
class VendaControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String clienteId, String vendedorId, String produtoId) {
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

        User userLogin = new User();
        userLogin.setTenantId(tenant.getId());
        userLogin.setName("Marina");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.SALE, Action.CREATE));
        userRepository.saveAndFlush(userLogin);

        User vendedor = new User();
        vendedor.setTenantId(tenant.getId());
        vendedor.setName("Carla Vendedora");
        vendedor.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        vendedor.setPasswordHash("hash");
        vendedor.setRole(Role.SALES_REP);
        vendedor.setProfile(Profile.SALES);
        userRepository.saveAndFlush(vendedor);

        Parceiro cliente = new Parceiro();
        cliente.setTenantId(tenant.getId());
        cliente.setTipoPessoa(TipoPessoa.JURIDICA);
        cliente.setDocumento(cnpjEmpresa.equals("11222333000144") ? "55666777000155" : "11222333000144");
        cliente.setNomeFantasia("Mercado Silva");
        cliente.getPapeis().add(PapelParceiro.CLIENTE);
        parceiroRepository.saveAndFlush(cliente);

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Venda dentro do estado");
        registration.setCfop("5102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Camiseta Polo");
        produto.setSku("P0001-" + codigo);
        produto.setPrecoVenda(new BigDecimal("59.90"));
        produto.setFiscalRegistration(registration);
        produtoRepository.saveAndFlush(produto);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        return new Contexto(token, cliente.getId().toString(), vendedor.getId().toString(), produto.getId().toString());
    }

    private String criarPedidoEmPreparo(Contexto ctx, Cookie cookie) throws Exception {
        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pedidoId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/pedidos/" + pedidoId + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk());

        return pedidoId;
    }

    @Test
    void faturaListsAndFindsVenda() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());
        String pedidoId = criarPedidoEmPreparo(ctx, cookie);

        String created = mockMvc.perform(post("/api/vendas/faturar/" + pedidoId).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.pedidoId").value(pedidoId))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();
        String vendaId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/vendas").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value(1));

        mockMvc.perform(get("/api/vendas/" + vendaId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNome").value("Mercado Silva"));

        mockMvc.perform(get("/api/pedidos/" + pedidoId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FATURADO"));
    }

    @Test
    void faturingAPedidoStillInDigitadoIsBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 } ]
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pedidoId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/vendas/faturar/" + pedidoId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void faturingWithoutSalePermissionIsForbidden() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-venda");
        tenant.setNome("sem-permissao-venda");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setRazaoSocial("sem-permissao-venda Ltda");
        empresa.setCnpj("99888777000166");
        empresaRepository.saveAndFlush(empresa);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Sem Permissão");
        user.setEmail("sem-permissao-venda@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        user.setProfile(Profile.VIEWER);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        String cookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-permissao-venda@aurora.com.br\",\"senha\":\"senha123\",\"manterConectado\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookieHeader.split("mesh_token=")[1].split(";")[0];
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/vendas").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
