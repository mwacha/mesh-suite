package com.meshsuite.parceiro;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.TenantContext;
import com.meshsuite.parceiro.dto.ParceiroContatoDto;
import com.meshsuite.parceiro.dto.ParceiroRequest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class ParceiroServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroService parceiroService;
    @Autowired EntityManager entityManager;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
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

        var resumo = parceiroService.resumo();

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.ativos()).isEqualTo(1);
        assertThat(resumo.bloqueados()).isEqualTo(1);
    }

    @Test
    void listaComFiltroDeBusca() {
        setUpTenant("aurora");
        parceiroService.criar(TenantContext.get(), request("11222333000144", Set.of(PapelParceiro.CLIENTE)));

        var pagina = parceiroService.listar("silva", null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).nomeFantasia()).isEqualTo("Mercado Silva");
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
}
