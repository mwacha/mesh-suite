package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CorEstampaRepository corEstampaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository,
                           CorEstampaRepository corEstampaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.corEstampaRepository = corEstampaRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProdutoSummaryResponse> listar(String busca, StatusProduto status, Pageable pageable) {
        Specification<Produto> spec = Specification.allOf(
                ProdutoSpecifications.comBusca(busca),
                ProdutoSpecifications.comStatus(status));
        return produtoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoResumoResponse resumo() {
        long ativos = produtoRepository.countByStatus(StatusProduto.ATIVO);
        long inativos = produtoRepository.countByStatus(StatusProduto.INATIVO);
        return new ProdutoResumoResponse(ativos + inativos, ativos, inativos);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProdutoResponse criar(UUID tenantId, ProdutoRequest request) {
        validarSku(request.sku(), null);

        Produto produto = new Produto();
        produto.setTenantId(tenantId);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProdutoResponse atualizar(UUID id, ProdutoRequest request) {
        validarSku(request.sku(), id);

        Produto produto = buscarEntidadePorId(id);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProdutoResponse atualizarStatus(UUID id, StatusProduto novoStatus) {
        Produto produto = buscarEntidadePorId(id);
        produto.setStatus(novoStatus);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        produtoRepository.delete(buscarEntidadePorId(id));
    }

    private Produto buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id).orElseThrow(ProdutoNaoEncontradoException::new);
    }

    private void validarSku(String sku, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? produtoRepository.existsBySku(sku)
                : produtoRepository.existsBySkuAndIdNot(sku, idAtual);
        if (duplicado) {
            throw new SkuDuplicadoException();
        }
    }

    private void aplicar(Produto produto, ProdutoRequest request) {
        produto.setNome(request.nome());
        produto.setSku(request.sku());
        produto.setCodigoBarras(request.codigoBarras());
        produto.setMarca(request.marca());
        produto.setCategoria(request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId()).orElseThrow(CategoriaNaoEncontradaException::new)
                : null);
        produto.setCorEstampa(request.corEstampaId() != null
                ? corEstampaRepository.findById(request.corEstampaId()).orElseThrow(CorEstampaNaoEncontradaException::new)
                : null);
        produto.setPrecoVenda(request.precoVenda());
        produto.setPrecoCusto(request.precoCusto());
        produto.setStatus(request.status() != null ? request.status() : StatusProduto.ATIVO);
        produto.setDescricao(request.descricao());
        produto.setQuantidadeEstoque(request.quantidadeEstoque() != null ? request.quantidadeEstoque() : BigDecimal.ZERO);
        produto.setUnidadeMedida(request.unidadeMedida() != null ? request.unidadeMedida() : UnidadeMedida.UN);
        produto.setEstoqueMinimo(request.estoqueMinimo());
        produto.setEstoqueMaximo(request.estoqueMaximo());
        produto.setPeso(request.peso());
        produto.setComprimento(request.comprimento());
        produto.setLargura(request.largura());
        produto.setAltura(request.altura());
    }

    private ProdutoSummaryResponse toSummary(Produto p) {
        return new ProdutoSummaryResponse(
                p.getId(), p.getNome(), p.getSku(), p.getMarca(), p.getPrecoVenda(), p.getQuantidadeEstoque(), p.getStatus());
    }

    private ProdutoResponse toResponse(Produto p) {
        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getSku(), p.getCodigoBarras(), p.getMarca(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getId() : null,
                p.getCorEstampa() != null ? p.getCorEstampa().getNome() : null,
                p.getPrecoVenda(), p.getPrecoCusto(), p.getStatus(), p.getDescricao(), p.getQuantidadeEstoque(),
                p.getUnidadeMedida(), p.getEstoqueMinimo(), p.getEstoqueMaximo(), p.getPeso(), p.getComprimento(),
                p.getLargura(), p.getAltura());
    }
}
