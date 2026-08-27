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
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.*;
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

/**
 * {@link ProductTypeStrategy} for {@link ProductType#PRODUCT} -- a standalone product
 * with its own price and stock, no components and no parent/children. This is the
 * only type that existed before types were introduced, so its listing/summary/CRUD
 * behavior is unchanged; every query is scoped to type=PRODUCT so Kit/Variation rows
 * never leak into it.
 */
@Service
public class SimpleProductService extends AbstractProductTypeService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ColorwayRepository colorwayRepository;

    public SimpleProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                 ColorwayRepository colorwayRepository) {
        super(productRepository);
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.colorwayRepository = colorwayRepository;
    }

    @Override
    public ProductType type() {
        return ProductType.PRODUCT;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProductListItemResponse> list(String search, ProductStatus status, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.hasType(ProductType.PRODUCT),
                ProductSpecifications.hasSearch(search),
                ProductSpecifications.hasStatus(status));
        return productRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductSummaryResponse summary() {
        long active = productRepository.countByStatusAndType(ProductStatus.ACTIVE, ProductType.PRODUCT);
        long inactive = productRepository.countByStatusAndType(ProductStatus.INACTIVE, ProductType.PRODUCT);
        return new ProductSummaryResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ProductResponse findById(UUID id) {
        return toResponse(findEntityByType(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ProductResponse create(UUID tenantId, ProductRequest request) {
        validateSku(request.sku(), null);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setType(ProductType.PRODUCT);
        apply(product, request);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ProductResponse update(UUID id, ProductRequest request) {
        validateSku(request.sku(), id);

        Product product = findEntityByType(id);
        apply(product, request);
        return toResponse(productRepository.saveAndFlush(product));
    }

    private void validateSku(String sku, UUID currentId) {
        boolean duplicate = currentId == null
                ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNot(sku, currentId);
        if (duplicate) {
            throw new DuplicateSkuException();
        }
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setBrand(request.brand());
        product.setCategory(request.categoryId() != null
                ? categoryRepository.findById(request.categoryId()).orElseThrow(CategoryNotFoundException::new)
                : null);
        product.setColorway(request.colorwayId() != null
                ? colorwayRepository.findById(request.colorwayId()).orElseThrow(ColorwayNotFoundException::new)
                : null);
        product.setSalePrice(request.salePrice());
        product.setCostPrice(request.costPrice());
        product.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        product.setDescription(request.description());
        product.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : BigDecimal.ZERO);
        product.setMeasurementUnit(request.measurementUnit() != null ? request.measurementUnit() : MeasurementUnit.UN);
        product.setSaleMultiple(request.saleMultiple() != null ? request.saleMultiple() : BigDecimal.ONE);
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        product.setSize(request.size());
        product.setWeight(request.weight());
        product.setLength(request.length());
        product.setWidth(request.width());
        product.setHeight(request.height());
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
                p.getMeasurementUnit(), p.getMinStock(), p.getMaxStock(), p.getSize(), p.getWeight(), p.getLength(),
                p.getWidth(), p.getHeight(), p.getSaleMultiple());
    }
}
