package com.meshsuite.pedido.controller;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.filter.JwtAuthenticationFilter;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.repository.UserRepository;
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
class PedidoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired ProductRepository produtoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private record Contexto(String cookie, String clienteId, String vendedorId, String produtoId) {
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
        userLogin.setName("Marina");
        userLogin.setEmail(email);
        userLogin.setPasswordHash(passwordEncoder.encode("senha123"));
        userLogin.setRole(Role.ADMIN);
        userLogin.setProfile(Profile.ADMIN);
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.VIEW));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.EDIT));
        userLogin.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.DELETE));
        userRepository.saveAndFlush(userLogin);

        User vendedor = new User();
        vendedor.setTenantId(tenant.getId());
        vendedor.setName("Carla Vendedora");
        vendedor.setEmail("carla-" + codigo + "@" + codigo + ".com.br");
        vendedor.setPasswordHash("hash");
        vendedor.setRole(Role.SALES_REP);
        vendedor.setProfile(Profile.SALES);
        userRepository.saveAndFlush(vendedor);

        Partner cliente = new Partner();
        cliente.setTenantId(tenant.getId());
        cliente.setPersonType(PersonType.LEGAL_ENTITY);
        cliente.setDocument(companyCnpj.equals("11222333000144") ? "55666777000155" : "11222333000144");
        cliente.setTradeName("Mercado Silva");
        cliente.getRoles().add(PartnerRole.CUSTOMER);
        partnerRepository.saveAndFlush(cliente);

        Product produto = new Product();
        produto.setTenantId(tenant.getId());
        produto.setName("Camiseta Polo");
        produto.setSku("P0001-" + codigo);
        produto.setSalePrice(new BigDecimal("59.90"));
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

    private String pedidoPayload(Contexto ctx) {
        return """
                {
                  "clienteId": "%s",
                  "vendedorId": "%s",
                  "desconto": 0,
                  "itens": [
                    { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 }
                  ]
                }
                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId());
    }

    @Test
    void createsListsUpdatesAdvancesAndDeletesPedido() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.status").value("DIGITADO"))
                .andExpect(jsonPath("$.total").value(119.80))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/pedidos").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value(1));

        mockMvc.perform(put("/api/pedidos/" + id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 10.00,
                                  "itens": [
                                    { "produtoId": "%s", "quantidade": 2, "valorUnitario": 59.90 }
                                  ]
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(109.80));

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIGITADO\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/pedidos/" + id).cookie(cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/pedidos/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyItensWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": []
                                }
                                """.formatted(ctx.clienteId(), ctx.vendedorId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClienteWithoutClientePapelWithBadRequest() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": "%s",
                                  "vendedorId": "%s",
                                  "desconto": 0,
                                  "itens": [ { "produtoId": "%s", "quantidade": 1, "valorUnitario": 10.00 } ]
                                }
                                """.formatted(ctx.vendedorId(), ctx.vendedorId(), ctx.produtoId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantACannotAccessTenantBsPedido() throws Exception {
        Contexto ctxA = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookieA = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxA.cookie());

        String body = mockMvc.perform(post("/api/pedidos").cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctxA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        Contexto ctxB = loginAndSetUp("boreal", "carlos@boreal.com.br", "55666777000155");
        Cookie cookieB = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctxB.cookie());

        // Without this, Hibernate's first-level cache (shared across this whole
        // @Transactional test method) can return tenant A's already-managed
        // entity for this id without re-issuing SQL, masking RLS behind a false
        // 200 instead of the expected 404 -- see the Global Constraints note.
        entityManager.clear();

        mockMvc.perform(get("/api/pedidos/" + id).cookie(cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    private String loginWithoutOrderPermission(String codigo, String email, String companyCnpj) throws Exception {
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

    @Test
    void listingWithoutOrderViewPermissionIsForbidden() throws Exception {
        String token = loginWithoutOrderPermission("sem-permissao", "sem-permissao@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token);

        mockMvc.perform(get("/api/pedidos").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void advancingToFaturadoViaStatusEndpointIsRejected() throws Exception {
        Contexto ctx = loginAndSetUp("aurora", "marina@aurora.com.br", "11222333000144");
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, ctx.cookie());

        String created = mockMvc.perform(post("/api/pedidos").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoPayload(ctx)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/pedidos/" + id + "/status").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FATURADO\"}"))
                .andExpect(status().isBadRequest());
    }
}
