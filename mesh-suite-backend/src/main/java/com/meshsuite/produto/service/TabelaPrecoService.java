package com.meshsuite.produto.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.domain.TabelaPreco;
import com.meshsuite.produto.domain.TabelaPrecoItem;
import com.meshsuite.produto.dto.*;
import com.meshsuite.produto.exception.TabelaPrecoNaoEncontradaException;
import com.meshsuite.produto.exception.TabelaPrecoNomeDuplicadoException;
import com.meshsuite.produto.exception.TabelaPrecoValidationException;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.produto.repository.TabelaPrecoRepository;
import com.meshsuite.produto.repository.specification.TabelaPrecoSpecifications;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TabelaPrecoService {

    private final TabelaPrecoRepository tabelaPrecoRepository;
    private final ProdutoRepository produtoRepository;

    public TabelaPrecoService(TabelaPrecoRepository tabelaPrecoRepository, ProdutoRepository produtoRepository) {
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<TabelaPrecoSummaryResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<TabelaPreco> spec = Specification.allOf(
                TabelaPrecoSpecifications.comBusca(busca),
                TabelaPrecoSpecifications.comAtivo(ativo));
        return tabelaPrecoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public TabelaPrecoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public TabelaPrecoResponse criar(UUID tenantId, TabelaPrecoRequest request) {
        validarNome(request.nome(), null);

        TabelaPreco tabelaPreco = new TabelaPreco();
        tabelaPreco.setTenantId(tenantId);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public TabelaPrecoResponse atualizar(UUID id, TabelaPrecoRequest request) {
        validarNome(request.nome(), id);

        TabelaPreco tabelaPreco = buscarEntidadePorId(id);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        tabelaPrecoRepository.delete(buscarEntidadePorId(id));
    }

    private TabelaPreco buscarEntidadePorId(UUID id) {
        return tabelaPrecoRepository.findById(id).orElseThrow(TabelaPrecoNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? tabelaPrecoRepository.existsByNome(nome)
                : tabelaPrecoRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new TabelaPrecoNomeDuplicadoException();
        }
    }

    // Clears and rebuilds the whole item list on every save -- same
    // "regenerate everything" funnel PurchaseOrderService.apply() uses.
    // No price calculation happens here: precoNestaTabela/percentualComissao
    // are persisted exactly as the client sent them (see Global Constraints).
    private void aplicar(TabelaPreco tabelaPreco, TabelaPrecoRequest request) {
        tabelaPreco.setNome(request.nome());
        tabelaPreco.setModoSelecaoProdutos(request.modoSelecaoProdutos());
        tabelaPreco.setMetodoAjuste(request.metodoAjuste());
        tabelaPreco.setOperacaoAjuste(request.operacaoAjuste());
        tabelaPreco.setTipoValorAjuste(request.tipoValorAjuste());
        tabelaPreco.setValorAjuste(request.valorAjuste());
        tabelaPreco.setArredondamento(request.arredondamento());
        tabelaPreco.setInicioVigencia(request.inicioVigencia());
        tabelaPreco.setTerminoVigencia(request.terminoVigencia());
        tabelaPreco.setValorMinimoVenda(request.valorMinimoVenda());
        tabelaPreco.setPercentualComissaoPadrao(request.percentualComissaoPadrao());
        tabelaPreco.setAtivo(request.ativo() != null ? request.ativo() : true);

        tabelaPreco.getItens().clear();
        for (TabelaPrecoItemInput itemInput : request.itens()) {
            Produto produto = produtoRepository.findById(itemInput.produtoId())
                    .orElseThrow(() -> new TabelaPrecoValidationException("Produto não encontrado"));
            TabelaPrecoItem item = new TabelaPrecoItem();
            item.setTabelaPreco(tabelaPreco);
            item.setProduto(produto);
            item.setPrecoNestaTabela(itemInput.precoNestaTabela());
            item.setPercentualComissao(itemInput.percentualComissao());
            tabelaPreco.getItens().add(item);
        }
    }

    private TabelaPrecoSummaryResponse toSummary(TabelaPreco t) {
        return new TabelaPrecoSummaryResponse(t.getId(), t.getNome(), t.getMetodoAjuste(), t.getOperacaoAjuste(),
                t.getTipoValorAjuste(), t.getValorAjuste(), t.getInicioVigencia(), t.getTerminoVigencia(), t.getAtivo());
    }

    private TabelaPrecoResponse toResponse(TabelaPreco t) {
        List<TabelaPrecoItemResponse> itens = t.getItens().stream()
                .map(i -> new TabelaPrecoItemResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getProduto().getSku(), i.getProduto().getPrecoVenda(), i.getPrecoNestaTabela(),
                        i.getPercentualComissao()))
                .toList();
        return new TabelaPrecoResponse(t.getId(), t.getNome(), t.getModoSelecaoProdutos(), t.getMetodoAjuste(),
                t.getOperacaoAjuste(), t.getTipoValorAjuste(), t.getValorAjuste(), t.getArredondamento(),
                t.getInicioVigencia(), t.getTerminoVigencia(), t.getValorMinimoVenda(), t.getPercentualComissaoPadrao(),
                t.getAtivo(), t.getCriadoEm(), itens);
    }
}
