package com.meshsuite.parceiro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.parceiro.domain.enums.IndicadorIe;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.dto.ParceiroContatoDto;
import com.meshsuite.parceiro.dto.ParceiroRequest;
import com.meshsuite.parceiro.exception.DocumentoDuplicadoException;
import com.meshsuite.parceiro.exception.ParceiroNaoEncontradoException;
import com.meshsuite.parceiro.exception.ParceiroValidacaoException;
import com.meshsuite.parceiro.service.ParceiroService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ParceiroServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroService parceiroService;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Test Caller");
        caller.setEmail("caller-" + UUID.randomUUID() + "@" + codigo + ".com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private ParceiroRequest request(String documento, Set<PapelParceiro> papeis) {
        return new ParceiroRequest(
                TipoPessoa.JURIDICA, documento, "Mercado Silva", "Mercado Silva Ltda", papeis,
                "financeiro@mercadosilva.com.br", "(11) 99999-9999", IndicadorIe.CONTRIBUINTE,
                "123456789", null, null,
                "01310100", "Av. Paulista", "1000", "Bela Vista", null, "SP", "São Paulo",
                "Cliente antigo", List.of(new ParceiroContatoDto("Ana Souza", "ana@mercadosilva.com.br",
                        "(11) 3333-3333", "(11) 98888-8888", "Financeiro")));
    }

    @Test
    void criaERecuperaParceiro() {
        setUpTenant("aurora");

        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var buscado = parceiroService.buscarPorId(criado.id());
        assertThat(buscado.nomeFantasia()).isEqualTo("Mercado Silva");
        assertThat(buscado.papeis()).containsExactly(PapelParceiro.CLIENTE);
        assertThat(buscado.contatos()).hasSize(1);
    }

    @Test
    void aceitaCnpjComMascaraEArmazenaSomenteDigitos() {
        setUpTenant("aurora");

        var criado = parceiroService.criar(TenantContext.get(),
                request("00.062.452/0001-06", Set.of(PapelParceiro.FORNECEDOR)));

        assertThat(criado.documento()).isEqualTo("00062452000106");
    }

    @Test
    void rejeitaParceiroSemPapelClienteOuFornecedor() {
        setUpTenant("aurora");

        assertThrows(ParceiroValidacaoException.class,
                () -> parceiroService.criar(TenantContext.get(),
                        request("11222333000144", Set.of(PapelParceiro.TRANSPORTADORA))));
    }

    @Test
    void rejeitaDocumentoDuplicadoNoMesmoTenant() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        assertThrows(DocumentoDuplicadoException.class,
                () -> parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.FORNECEDOR))));
    }

    @Test
    void rejeitaAtualizacaoDeStatusParaEmRisco() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        assertThrows(ParceiroValidacaoException.class,
                () -> parceiroService.atualizarStatus(criado.id(), StatusParceiro.EM_RISCO));
    }

    @Test
    void atualizaStatusParaBloqueado() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var atualizado = parceiroService.atualizarStatus(criado.id(), StatusParceiro.BLOQUEADO);

        assertThat(atualizado.status()).isEqualTo(StatusParceiro.BLOQUEADO);
    }

    @Test
    void resumoContaPorStatus() {
        setUpTenant("aurora");
        var a = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.FORNECEDOR)));
        parceiroService.atualizarStatus(a.id(), StatusParceiro.BLOQUEADO);

        var resumo = parceiroService.resumo(null);

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.ativos()).isEqualTo(1);
        assertThat(resumo.bloqueados()).isEqualTo(1);
    }

    @Test
    void resumoContaSomenteOPapelInformado() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        var clienteBloqueado = parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("00062452000106", Set.of(PapelParceiro.FORNECEDOR)));
        parceiroService.atualizarStatus(clienteBloqueado.id(), StatusParceiro.BLOQUEADO);

        var resumoClientes = parceiroService.resumo(PapelParceiro.CLIENTE);

        assertThat(resumoClientes.total()).isEqualTo(2);
        assertThat(resumoClientes.ativos()).isEqualTo(1);
        assertThat(resumoClientes.bloqueados()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBusca() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var pagina = parceiroService.listar("silva", null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).nomeFantasia()).isEqualTo("Mercado Silva");
    }

    @Test
    void listaComFiltroDeDocumentoParcialIgnorandoMascara() {
        setUpTenant("aurora");
        var alvo = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.CLIENTE)));

        var comMascara = parceiroService.listar(null, null, null, "112.223.330/0014-4", null, null, null, PageRequest.of(0, 10));
        assertThat(comMascara.getTotalElements()).isEqualTo(1);
        assertThat(comMascara.getContent().get(0).id()).isEqualTo(alvo.id());

        var parcial = parceiroService.listar(null, null, null, "22333", null, null, null, PageRequest.of(0, 10));
        assertThat(parcial.getTotalElements()).isEqualTo(1);
        assertThat(parcial.getContent().get(0).id()).isEqualTo(alvo.id());

        var semCorrespondencia = parceiroService.listar(null, null, null, "99999", null, null, null, PageRequest.of(0, 10));
        assertThat(semCorrespondencia.getTotalElements()).isEqualTo(0);
    }

    @Test
    void listaComFiltroDePapel() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.FORNECEDOR)));

        var pagina = parceiroService.listar(null, null, null, null, null, null, PapelParceiro.CLIENTE, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeStatusMultiplo() {
        setUpTenant("aurora");
        var ativo1 = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        var ativo2 = parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.CLIENTE)));
        var bloqueado = parceiroService.criar(TenantContext.get(), request("00062452000106", Set.of(PapelParceiro.CLIENTE)));
        parceiroService.atualizarStatus(bloqueado.id(), StatusParceiro.BLOQUEADO);

        var apenasBloqueado = parceiroService.listar(null, List.of(StatusParceiro.BLOQUEADO), null, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(apenasBloqueado.getTotalElements()).isEqualTo(1);
        assertThat(apenasBloqueado.getContent().get(0).id()).isEqualTo(bloqueado.id());

        var ativoEBloqueado = parceiroService.listar(null, List.of(StatusParceiro.ATIVO, StatusParceiro.BLOQUEADO), null, null, null,
                null, null, PageRequest.of(0, 10));
        assertThat(ativoEBloqueado.getTotalElements()).isEqualTo(3);
        assertThat(ativoEBloqueado.getContent()).extracting("id")
                .containsExactlyInAnyOrder(ativo1.id(), ativo2.id(), bloqueado.id());
    }

    @Test
    void listaComFiltroDeUfMultiplo() {
        setUpTenant("aurora");
        var sp = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        var outraUf = new ParceiroRequest(
                TipoPessoa.JURIDICA, "55666777000155", "Comércio Rio", "Comércio Rio Ltda", Set.of(PapelParceiro.CLIENTE),
                "financeiro@comerciorio.com.br", "(21) 99999-9999", IndicadorIe.CONTRIBUINTE, "987654321", null, null,
                "20000000", "Av. Rio Branco", "1", "Centro", null, "RJ", "Rio de Janeiro",
                null, List.of());
        var rj = parceiroService.criar(TenantContext.get(), outraUf);

        var pagina = parceiroService.listar(null, null, null, null, List.of("SP", "RJ"), null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting("id").containsExactlyInAnyOrder(sp.id(), rj.id());
    }

    @Test
    void excluiParceiro() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        parceiroService.excluir(criado.id());

        assertThrows(ParceiroNaoEncontradoException.class, () -> parceiroService.buscarPorId(criado.id()));
    }

    @Test
    void atualizaParceiroComSucesso() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var requestAtualizado = new ParceiroRequest(
                TipoPessoa.JURIDICA, "11222333000144", "Mercado Silva Atualizado", "Mercado Silva Ltda",
                Set.of(PapelParceiro.CLIENTE), "financeiro@mercadosilva.com.br", "(11) 99999-9999",
                IndicadorIe.CONTRIBUINTE, "123456789", null, null,
                "01310100", "Av. Paulista", "1000", "Bela Vista", null, "SP", "São Paulo",
                "Cliente antigo", List.of(new ParceiroContatoDto("Ana Souza", "ana@mercadosilva.com.br",
                        "(11) 3333-3333", "(11) 98888-8888", "Financeiro")));

        parceiroService.atualizar(criado.id(), requestAtualizado);

        var buscado = parceiroService.buscarPorId(criado.id());
        assertThat(buscado.nomeFantasia()).isEqualTo("Mercado Silva Atualizado");
    }

    @Test
    void atualizaParceiroMantendoOProprioDocumento() {
        setUpTenant("aurora");
        var criado = parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var atualizado = parceiroService.atualizar(criado.id(),
                request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        assertThat(atualizado.documento()).isEqualTo("11222333000144");
    }

    @Test
    void rejeitaAtualizacaoParaDocumentoDeOutroParceiro() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));
        var outro = parceiroService.criar(TenantContext.get(), request("55666777000155", Set.of(PapelParceiro.FORNECEDOR)));

        assertThrows(DocumentoDuplicadoException.class,
                () -> parceiroService.atualizar(outro.id(), request("11222333000144", Set.of(PapelParceiro.FORNECEDOR))));
    }

    @Test
    void deniesListingWhenCallerLacksCustomerViewPermission() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User noPerms = new User();
        noPerms.setTenantId(tenant.getId());
        noPerms.setName("No Permissions");
        noPerms.setEmail("no-perms@sem-permissao.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> parceiroService.listar(null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
