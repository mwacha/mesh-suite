package com.meshsuite.product.service;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import java.util.UUID;

/**
 * One implementation per {@link ProductType} (Strategy pattern). Covers only the
 * operations that are genuinely uniform across types -- deactivating or deleting a
 * row needs no type-specific request body. Creation/update/lookup differ enough per
 * type (Kit has no price of its own, Variation has parent+children) that they live as
 * separate methods on each concrete service instead of being forced into this
 * contract.
 */
public interface ProductTypeStrategy {

    ProductType type();

    void delete(UUID id);

    void updateStatus(UUID id, ProductStatus status);
}
