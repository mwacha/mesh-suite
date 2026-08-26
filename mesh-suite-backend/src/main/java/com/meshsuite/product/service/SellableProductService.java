package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.SellableProductResponse;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.product.repository.specification.ProductSpecifications;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flat item-picker listing for order entry (Pedidos, and any future Compras item search):
 * every row a line item can actually point to -- Simples, Kit, and Variação *children* -- each
 * as its own standalone entry. Distinct from {@link ProductListingService}, which nests children
 * under their Variação parent for the Produtos screen and never lists a child as a row on its
 * own; here the parent itself is excluded instead, since a Variação parent has no price/stock of
 * its own and can't be ordered directly.
 */
@Service
public class SellableProductService {

    private static final List<ProductType> SELLABLE_TYPES =
            List.of(ProductType.PRODUCT, ProductType.PRODUCT_KIT, ProductType.VARIATION_CHILD);

    private final ProductRepository productRepository;

    public SellableProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<SellableProductResponse> list(String search, ProductStatus status, List<ProductType> types,
                                                Pageable pageable) {
        // Intersected rather than trusted: a caller may narrow the set (the Kit composer
        // asks for PRODUCT + VARIATION_CHILD, since a kit can contain neither another kit
        // nor a variação-pai) but can never widen it back to a non-sellable type.
        List<ProductType> requested = types == null || types.isEmpty()
                ? SELLABLE_TYPES
                : types.stream().filter(SELLABLE_TYPES::contains).toList();
        if (requested.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.hasTypeIn(requested),
                ProductSpecifications.hasSearch(search),
                ProductSpecifications.hasStatus(status));
        return productRepository.findAll(spec, pageable).map(this::toSummary);
    }

    private SellableProductResponse toSummary(Product p) {
        return new SellableProductResponse(
                p.getId(), p.getName(), p.getSku(), p.getType(), p.getSalePrice(), p.getStockQuantity(),
                p.getStatus(), p.getSize(), p.getColorway() != null ? p.getColorway().getName() : null);
    }
}
