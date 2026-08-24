package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.ProdutoKitItemRequest;
import com.meshsuite.produto.dto.ProdutoKitItemResponse;
import com.meshsuite.produto.dto.ProdutoKitRequest;
import com.meshsuite.produto.dto.ProdutoKitResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProdutoKitService {

    private final ProdutoRepository produtoRepository;

    public ProdutoKitService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProdutoKitResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProdutoKitResponse criar(UUID tenantId, ProdutoKitRequest request) {
        validarSku(request.sku());

        Produto kit = new Produto();
        kit.setTenantId(tenantId);
        kit.setTipo(ProdutoTipo.PRODUCT_KIT);
        aplicar(kit, request);
        return toResponse(produtoRepository.saveAndFlush(kit));
    }

    private Produto buscarEntidadePorId(UUID id) {
        Produto produto = produtoRepository.findById(id).orElseThrow(ProdutoNaoEncontradoException::new);
        if (produto.getTipo() != ProdutoTipo.PRODUCT_KIT) {
            throw new ProdutoNaoEncontradoException();
        }
        return produto;
    }

    private void validarSku(String sku) {
        if (produtoRepository.existsBySku(sku)) {
            throw new SkuDuplicadoException();
        }
    }

    private void aplicar(Produto kit, ProdutoKitRequest request) {
        kit.setNome(request.nome());
        kit.setSku(request.sku());
        kit.setCodigoBarras(request.codigoBarras());
        kit.setUnidadeMedida(request.unidadeMedida() != null ? request.unidadeMedida() : UnidadeMedida.UN);
        kit.setStatus(request.status() != null ? request.status() : StatusProduto.ATIVO);
        kit.setDescricao(request.descricao());

        kit.getItensKit().clear();
        for (ProdutoKitItemRequest dto : request.itens()) {
            Produto componente = produtoRepository.findById(dto.produtoId())
                    .orElseThrow(ProdutoNaoEncontradoException::new);
            if (componente.getTipo() != ProdutoTipo.PRODUCT) {
                throw new ProdutoValidacaoException(
                        "Um kit só pode ser composto por produtos simples, não por outro kit ou produto com variação");
            }
            ProdutoKitItem item = new ProdutoKitItem();
            item.setProdutoKit(kit);
            item.setProduto(componente);
            item.setQuantidade(dto.quantidade());
            kit.getItensKit().add(item);
        }

        // precoVenda is "calculado automaticamente" / "bloqueado" per the
        // wireframe: derived from the current component prices and stored at
        // save time (not recomputed live on every read), the same way
        // PurchaseOrder freezes subtotal/total -- other modules that just read
        // produto.precoVenda generically (Pedidos, relatórios) need a real
        // materialized value, not special-cased kit logic.
        kit.setPrecoVenda(kit.getItensKit().stream()
                .map(i -> i.getQuantidade().multiply(i.getProduto().getPrecoVenda()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private ProdutoKitResponse toResponse(Produto kit) {
        List<ProdutoKitItemResponse> itens = kit.getItensKit().stream()
                .map(this::toItemResponse)
                .toList();
        return new ProdutoKitResponse(kit.getId(), kit.getNome(), kit.getSku(), kit.getCodigoBarras(),
                kit.getUnidadeMedida(), kit.getStatus(), kit.getDescricao(), kit.getPrecoVenda(), itens);
    }

    private ProdutoKitItemResponse toItemResponse(ProdutoKitItem item) {
        Produto produto = item.getProduto();
        BigDecimal totalItem = item.getQuantidade().multiply(produto.getPrecoVenda());
        return new ProdutoKitItemResponse(
                produto.getId(), produto.getNome(), produto.getSku(), item.getQuantidade(), produto.getPrecoVenda(), totalItem);
    }
}
