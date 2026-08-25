package com.meshsuite.pricetable.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.pricetable.domain.PriceTable;
import com.meshsuite.pricetable.domain.PriceTableItem;
import com.meshsuite.pricetable.dto.*;
import com.meshsuite.pricetable.exception.PriceTableNotFoundException;
import com.meshsuite.pricetable.exception.DuplicatePriceTableNameException;
import com.meshsuite.pricetable.exception.PriceTableValidationException;
import com.meshsuite.pricetable.repository.PriceTableRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.pricetable.repository.specification.PriceTableSpecifications;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceTableService {

    private final PriceTableRepository tabelaPrecoRepository;
    private final ProductRepository produtoRepository;

    public PriceTableService(PriceTableRepository tabelaPrecoRepository, ProductRepository produtoRepository) {
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<PriceTableSummaryResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<PriceTable> spec = Specification.allOf(
                PriceTableSpecifications.comBusca(busca),
                PriceTableSpecifications.comAtivo(ativo));
        return tabelaPrecoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public PriceTableResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public PriceTableCountsResponse counts() {
        long active = tabelaPrecoRepository.countByActive(true);
        long inactive = tabelaPrecoRepository.countByActive(false);
        return new PriceTableCountsResponse(active + inactive, active, inactive);
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public PriceTableResponse criar(UUID tenantId, PriceTableRequest request) {
        validarNome(request.name(), null);

        PriceTable tabelaPreco = new PriceTable();
        tabelaPreco.setTenantId(tenantId);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public PriceTableResponse atualizar(UUID id, PriceTableRequest request) {
        validarNome(request.name(), id);

        PriceTable tabelaPreco = buscarEntidadePorId(id);
        aplicar(tabelaPreco, request);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        tabelaPrecoRepository.delete(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public PriceTableResponse atualizarStatus(UUID id, boolean active) {
        PriceTable tabelaPreco = buscarEntidadePorId(id);
        tabelaPreco.setActive(active);
        return toResponse(tabelaPrecoRepository.saveAndFlush(tabelaPreco));
    }

    private PriceTable buscarEntidadePorId(UUID id) {
        return tabelaPrecoRepository.findById(id).orElseThrow(PriceTableNotFoundException::new);
    }

    private void validarNome(String name, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? tabelaPrecoRepository.existsByName(name)
                : tabelaPrecoRepository.existsByNameAndIdNot(name, idAtual);
        if (duplicado) {
            throw new DuplicatePriceTableNameException();
        }
    }

    // Clears and rebuilds the whole item list on every save -- same
    // "regenerate everything" funnel PurchaseOrderService.apply() uses.
    // No price calculation happens here: tablePrice/commissionPercentage
    // are persisted exactly as the client sent them (see Global Constraints).
    private void aplicar(PriceTable tabelaPreco, PriceTableRequest request) {
        tabelaPreco.setName(request.name());
        tabelaPreco.setProductSelectionMode(request.productSelectionMode());
        tabelaPreco.setAdjustmentMethod(request.adjustmentMethod());
        tabelaPreco.setAdjustmentOperation(request.adjustmentOperation());
        tabelaPreco.setAdjustmentValueType(request.adjustmentValueType());
        tabelaPreco.setAdjustmentValue(request.adjustmentValue());
        tabelaPreco.setRounding(request.rounding());
        tabelaPreco.setEffectiveStartDate(request.effectiveStartDate());
        tabelaPreco.setEffectiveEndDate(request.effectiveEndDate());
        tabelaPreco.setMinSalePrice(request.minSalePrice());
        tabelaPreco.setDefaultCommissionPercentage(request.defaultCommissionPercentage());
        tabelaPreco.setActive(request.active() != null ? request.active() : true);

        tabelaPreco.getItems().clear();
        for (PriceTableItemInput itemInput : request.items()) {
            Product produto = produtoRepository.findById(itemInput.productId())
                    .orElseThrow(() -> new PriceTableValidationException("Produto não encontrado"));
            PriceTableItem item = new PriceTableItem();
            item.setPriceTable(tabelaPreco);
            item.setProduct(produto);
            item.setTablePrice(itemInput.tablePrice());
            item.setCommissionPercentage(itemInput.commissionPercentage());
            tabelaPreco.getItems().add(item);
        }
    }

    private PriceTableSummaryResponse toSummary(PriceTable t) {
        return new PriceTableSummaryResponse(t.getId(), t.getName(), t.getAdjustmentMethod(), t.getAdjustmentOperation(),
                t.getAdjustmentValueType(), t.getAdjustmentValue(), t.getEffectiveStartDate(), t.getEffectiveEndDate(), t.getActive());
    }

    private PriceTableResponse toResponse(PriceTable t) {
        List<PriceTableItemResponse> items = t.getItems().stream()
                .map(i -> new PriceTableItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getProduct().getSku(), i.getProduct().getSalePrice(), i.getTablePrice(),
                        i.getCommissionPercentage()))
                .toList();
        return new PriceTableResponse(t.getId(), t.getName(), t.getProductSelectionMode(), t.getAdjustmentMethod(),
                t.getAdjustmentOperation(), t.getAdjustmentValueType(), t.getAdjustmentValue(), t.getRounding(),
                t.getEffectiveStartDate(), t.getEffectiveEndDate(), t.getMinSalePrice(), t.getDefaultCommissionPercentage(),
                t.getActive(), t.getCreatedAt(), items);
    }
}
