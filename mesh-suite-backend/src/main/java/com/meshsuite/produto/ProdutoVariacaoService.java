package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.ProdutoVariacaoRequest;
import com.meshsuite.produto.dto.ProdutoVariacaoResponse;
import com.meshsuite.produto.dto.TipoVariacaoRequest;
import com.meshsuite.produto.dto.TipoVariacaoResponse;
import com.meshsuite.produto.dto.VarianteRequest;
import com.meshsuite.produto.dto.VarianteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProdutoVariacaoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoVariacaoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoVariacaoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProdutoVariacaoResponse criar(UUID tenantId, ProdutoVariacaoRequest request) {
        validarSkus(request, null);
        validarIntegridade(request);

        Produto produto = new Produto();
        produto.setTenantId(tenantId);
        produto.setTipo(ProdutoTipo.VARIATION_PARENT);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProdutoVariacaoResponse atualizar(UUID id, ProdutoVariacaoRequest request) {
        Produto produto = buscarEntidadePorId(id);
        validarSkus(request, produto);
        validarIntegridade(request);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    private Produto buscarEntidadePorId(UUID id) {
        Produto produto = produtoRepository.findById(id).orElseThrow(ProdutoNaoEncontradoException::new);
        if (produto.getTipo() != ProdutoTipo.VARIATION_PARENT) {
            throw new ProdutoNaoEncontradoException();
        }
        return produto;
    }

    // `existente` is null when creating (nothing to exclude from the duplicate
    // check) and the produto being edited when updating -- an unchanged SKU,
    // parent or variant, must not collide with itself/its own current children.
    private void validarSkus(ProdutoVariacaoRequest request, Produto existente) {
        boolean skuParentDuplicado = existente == null
                ? produtoRepository.existsBySku(request.sku())
                : produtoRepository.existsBySkuAndIdNot(request.sku(), existente.getId());
        if (skuParentDuplicado) {
            throw new SkuDuplicadoException();
        }
        for (VarianteRequest variante : request.variantes()) {
            boolean skuVarianteDuplicado = existente == null
                    ? produtoRepository.existsBySku(variante.sku())
                    : produtoRepository.existsBySkuOutsideParent(variante.sku(), existente.getId());
            if (skuVarianteDuplicado) {
                throw new SkuDuplicadoException();
            }
        }
    }

    // Bean Validation (@NotBlank/@NotEmpty on the DTOs) already guarantees every
    // tipo has a name and at least one valor, and every variante has a sku/preço.
    // This catches the cross-field integrity issues annotations can't express:
    // duplicate values within one tipo, and duplicate SKUs across variantes in
    // the same request (validarSkus() above only checks against what's already
    // persisted, not duplicates within this same payload).
    private void validarIntegridade(ProdutoVariacaoRequest request) {
        for (TipoVariacaoRequest tipo : request.tiposVariacao()) {
            long valoresUnicos = tipo.valores().stream().distinct().count();
            if (valoresUnicos != tipo.valores().size()) {
                throw new ProdutoValidacaoException("O tipo \"" + tipo.nome() + "\" tem valores duplicados");
            }
        }

        long skusUnicos = request.variantes().stream().map(VarianteRequest::sku).distinct().count();
        if (skusUnicos != request.variantes().size()) {
            throw new ProdutoValidacaoException("Existem variantes com o mesmo SKU");
        }
    }

    private void aplicar(Produto produto, ProdutoVariacaoRequest request) {
        produto.setNome(request.nome());
        produto.setSku(request.sku());
        produto.setMarca(request.marca());
        produto.setCategoria(request.categoria());
        produto.setPrecoVenda(request.precoVenda());
        produto.setStatus(request.status() != null ? request.status() : StatusProduto.ATIVO);
        produto.setDescricao(request.descricao());
        produto.setUnidadeMedida(request.unidadeMedida() != null ? request.unidadeMedida() : UnidadeMedida.UN);

        aplicarTiposVariacao(produto, request.tiposVariacao());
        aplicarVariantes(produto, request.variantes());
    }

    // Merges by nome/valor instead of clearing+recreating: tipo_variacao has
    // UNIQUE(produto_id, nome) and tipo_variacao_valor UNIQUE(tipo_variacao_id,
    // valor). On an edit that keeps a tipo's name (the normal case), a blind
    // clear()+addAll() deletes and re-inserts a row with the *same* unique key
    // in one flush -- Hibernate doesn't guarantee the delete is flushed before
    // the insert, so that trips the constraint even though the net state has
    // no real duplicate. Reusing the existing entity for an unchanged key
    // turns that into a plain UPDATE instead.
    private void aplicarTiposVariacao(Produto produto, List<TipoVariacaoRequest> tiposRequest) {
        Map<String, TipoVariacao> existentesPorNome = produto.getTiposVariacao().stream()
                .collect(Collectors.toMap(TipoVariacao::getNome, t -> t));

        List<TipoVariacao> novaListaTipos = new ArrayList<>();
        int ordemTipo = 0;
        for (TipoVariacaoRequest tipoDto : tiposRequest) {
            TipoVariacao tipo = existentesPorNome.remove(tipoDto.nome());
            if (tipo == null) {
                tipo = new TipoVariacao();
                tipo.setProduto(produto);
                tipo.setNome(tipoDto.nome());
            }
            tipo.setOrdem(ordemTipo++);

            Map<String, TipoVariacaoValor> valoresExistentes = tipo.getValores().stream()
                    .collect(Collectors.toMap(TipoVariacaoValor::getValor, v -> v));
            List<TipoVariacaoValor> novaListaValores = new ArrayList<>();
            int ordemValor = 0;
            for (String valorDto : tipoDto.valores()) {
                TipoVariacaoValor valor = valoresExistentes.remove(valorDto);
                if (valor == null) {
                    valor = new TipoVariacaoValor();
                    valor.setTipoVariacao(tipo);
                    valor.setValor(valorDto);
                }
                valor.setOrdem(ordemValor++);
                novaListaValores.add(valor);
            }
            tipo.getValores().clear();
            tipo.getValores().addAll(novaListaValores);

            novaListaTipos.add(tipo);
        }

        produto.getTiposVariacao().clear();
        produto.getTiposVariacao().addAll(novaListaTipos);
    }

    // Merges by combinação instead of blindly clearing+recreating (like
    // tiposVariacao above): variant rows are real produtos that
    // pedido_item/stock_movement/purchase_order_item can point to (see
    // V20's migration note), with no ON DELETE CASCADE on those FKs. Combos
    // still present keep their existing produto row (and id) so those
    // references keep resolving; only combos genuinely removed by this edit
    // are dropped via orphanRemoval, which is exactly the case where an
    // existing FK to it *should* block the save.
    private void aplicarVariantes(Produto produto, List<VarianteRequest> variantesRequest) {
        Map<String, Produto> existentesPorCombinacao = produto.getVariantes().stream()
                .collect(Collectors.toMap(v -> String.join("|", v.getCombinacao()), v -> v));

        List<Produto> novaLista = new ArrayList<>();
        for (VarianteRequest varianteDto : variantesRequest) {
            String chave = String.join("|", varianteDto.combinacao());
            Produto child = existentesPorCombinacao.remove(chave);
            if (child == null) {
                child = new Produto();
                child.setTenantId(produto.getTenantId());
                child.setTipo(ProdutoTipo.VARIATION_CHILD);
                child.setParent(produto);
                child.setCombinacao(varianteDto.combinacao());
            }
            child.setNome(produto.getNome() + " - " + String.join(" / ", varianteDto.combinacao()));
            child.setMarca(produto.getMarca());
            child.setCategoria(produto.getCategoria());
            child.setStatus(produto.getStatus());
            child.setUnidadeMedida(produto.getUnidadeMedida());
            child.setSku(varianteDto.sku());
            child.setCodigoBarras(varianteDto.codigoBarras());
            child.setPrecoVenda(varianteDto.precoVenda());
            child.setPrecoCusto(varianteDto.precoCusto());
            child.setQuantidadeEstoque(
                    varianteDto.quantidadeEstoque() != null ? varianteDto.quantidadeEstoque() : BigDecimal.ZERO);
            child.setEstoqueMinimo(varianteDto.estoqueMinimo());
            child.setEstoqueMaximo(varianteDto.estoqueMaximo());
            child.setPeso(varianteDto.peso());
            child.setComprimento(varianteDto.comprimento());
            child.setLargura(varianteDto.largura());
            child.setAltura(varianteDto.altura());
            novaLista.add(child);
        }

        // Anything left in existentesPorCombinacao had no match in the
        // request -- not re-added below, so orphanRemoval deletes it on flush.
        produto.getVariantes().clear();
        produto.getVariantes().addAll(novaLista);
    }

    private ProdutoVariacaoResponse toResponse(Produto produto) {
        List<TipoVariacaoResponse> tipos = produto.getTiposVariacao().stream()
                .map(t -> new TipoVariacaoResponse(t.getNome(), t.getValores().stream().map(TipoVariacaoValor::getValor).toList()))
                .toList();
        List<VarianteResponse> variantes = produto.getVariantes().stream()
                .map(this::toVarianteResponse)
                .toList();
        return new ProdutoVariacaoResponse(produto.getId(), produto.getNome(), produto.getSku(), produto.getMarca(),
                produto.getCategoria(), produto.getPrecoVenda(), produto.getStatus(), produto.getDescricao(),
                produto.getUnidadeMedida(), tipos, variantes);
    }

    private VarianteResponse toVarianteResponse(Produto v) {
        return new VarianteResponse(v.getCombinacao(), v.getSku(), v.getCodigoBarras(), v.getPrecoVenda(),
                v.getPrecoCusto(), v.getQuantidadeEstoque(), v.getEstoqueMinimo(), v.getEstoqueMaximo(),
                v.getPeso(), v.getComprimento(), v.getLargura(), v.getAltura());
    }
}
