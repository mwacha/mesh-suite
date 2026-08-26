package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.ProductAllListItemResponse;
import com.meshsuite.product.dto.VariationChildSummaryResponse;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.product.repository.specification.ProductSpecifications;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only cross-type listing for the unified Produtos screen -- unlike
 * {@link SimpleProductService}, which is deliberately scoped to type=PRODUCT alone (other
 * pickers like the Tabela de Preço item search depend on that), this returns Simples, Kit and
 * Variação parent rows together with a type discriminator, so it lives outside the
 * {@link ProductTypeStrategy} hierarchy rather than as a fourth strategy.
 */
@Service
public class ProductListingService {

    private static final List<ProductType> LISTABLE_TYPES =
            List.of(ProductType.PRODUCT, ProductType.PRODUCT_KIT, ProductType.VARIATION_PARENT);

    private final ProductRepository productRepository;

    public ProductListingService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ProductAllListItemResponse> listAll(String search, ProductStatus status, ProductType type,
                                                      Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.hasTypeIn(LISTABLE_TYPES),
                type != null ? ProductSpecifications.hasType(type) : null,
                ProductSpecifications.hasSearch(search),
                ProductSpecifications.hasStatus(status));

        Page<Product> page = productRepository.findAll(spec, pageable);

        List<UUID> parentIds = page.getContent().stream()
                .filter(p -> p.getType() == ProductType.VARIATION_PARENT)
                .map(Product::getId)
                .toList();
        Map<UUID, List<VariationChildSummaryResponse>> childrenByParentId = parentIds.isEmpty()
                ? Map.of()
                : productRepository.findByParentProductIdIn(parentIds).stream()
                        .collect(Collectors.groupingBy(
                                child -> child.getParentProduct().getId(),
                                Collectors.mapping(this::toChildSummary, Collectors.toList())));

        return page.map(p -> toListItem(p, childrenByParentId.getOrDefault(p.getId(), List.of())));
    }

    private ProductAllListItemResponse toListItem(Product p, List<VariationChildSummaryResponse> children) {
        return new ProductAllListItemResponse(
                p.getId(), p.getName(), p.getSku(), p.getBrand(), p.getType(),
                p.getSalePrice(), p.getStockQuantity(), p.getStatus(), children);
    }

    private VariationChildSummaryResponse toChildSummary(Product child) {
        return new VariationChildSummaryResponse(
                child.getId(), child.getName(), child.getSku(), child.getSalePrice(), child.getStockQuantity());
    }
}
