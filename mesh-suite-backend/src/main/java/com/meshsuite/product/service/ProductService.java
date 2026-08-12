package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.dto.*;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.product.repository.specification.ProductSpecifications;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository produtoRepository;
    private final CategoryRepository categoriaRepository;
    private final ColorwayRepository corEstampaRepository;

    public ProductService(ProductRepository produtoRepository, CategoryRepository categoriaRepository,
                           ColorwayRepository corEstampaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.corEstampaRepository = corEstampaRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProductListItemResponse> listar(String busca, ProductStatus status, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.comBusca(busca),
                ProductSpecifications.comStatus(status));
        return produtoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductSummaryResponse resumo() {
        long ativos = produtoRepository.countByStatus(ProductStatus.ACTIVE);
        long inativos = produtoRepository.countByStatus(ProductStatus.INACTIVE);
        return new ProductSummaryResponse(ativos + inativos, ativos, inativos);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProductResponse criar(UUID tenantId, ProductRequest request) {
        validarSku(request.sku(), null);

        Product produto = new Product();
        produto.setTenantId(tenantId);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProductResponse atualizar(UUID id, ProductRequest request) {
        validarSku(request.sku(), id);

        Product produto = buscarEntidadePorId(id);
        aplicar(produto, request);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProductResponse atualizarStatus(UUID id, ProductStatus novoStatus) {
        Product produto = buscarEntidadePorId(id);
        produto.setStatus(novoStatus);
        return toResponse(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        produtoRepository.delete(buscarEntidadePorId(id));
    }

    private Product buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id).orElseThrow(ProductNotFoundException::new);
    }

    private void validarSku(String sku, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? produtoRepository.existsBySku(sku)
                : produtoRepository.existsBySkuAndIdNot(sku, idAtual);
        if (duplicado) {
            throw new DuplicateSkuException();
        }
    }

    private void aplicar(Product produto, ProductRequest request) {
        produto.setName(request.name());
        produto.setSku(request.sku());
        produto.setBarcode(request.barcode());
        produto.setBrand(request.brand());
        produto.setCategory(request.categoryId() != null
                ? categoriaRepository.findById(request.categoryId()).orElseThrow(CategoryNotFoundException::new)
                : null);
        produto.setColorway(request.colorwayId() != null
                ? corEstampaRepository.findById(request.colorwayId()).orElseThrow(ColorwayNotFoundException::new)
                : null);
        produto.setSalePrice(request.salePrice());
        produto.setCostPrice(request.costPrice());
        produto.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        produto.setDescription(request.description());
        produto.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : BigDecimal.ZERO);
        produto.setMeasurementUnit(request.measurementUnit() != null ? request.measurementUnit() : MeasurementUnit.UN);
        produto.setMinStock(request.minStock());
        produto.setMaxStock(request.maxStock());
        produto.setWeight(request.weight());
        produto.setLength(request.length());
        produto.setWidth(request.width());
        produto.setHeight(request.height());
    }

    private ProductListItemResponse toSummary(Product p) {
        return new ProductListItemResponse(
                p.getId(), p.getName(), p.getSku(), p.getBrand(), p.getSalePrice(), p.getStockQuantity(), p.getStatus());
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getSku(), p.getBarcode(), p.getBrand(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getColorway() != null ? p.getColorway().getId() : null,
                p.getColorway() != null ? p.getColorway().getName() : null,
                p.getSalePrice(), p.getCostPrice(), p.getStatus(), p.getDescription(), p.getStockQuantity(),
                p.getMeasurementUnit(), p.getMinStock(), p.getMaxStock(), p.getWeight(), p.getLength(),
                p.getWidth(), p.getHeight());
    }
}
