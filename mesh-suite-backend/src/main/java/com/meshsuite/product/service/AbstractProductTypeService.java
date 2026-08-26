package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.repository.ProductRepository;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared delete/updateStatus for every {@link ProductTypeStrategy} -- deleting or
 * deactivating a row needs no type-specific request body, so there's nothing for a
 * concrete subclass to override here. Each subclass only adds its own type-specific
 * create/update/findById (different DTOs per type, so those can't be pulled up).
 */
public abstract class AbstractProductTypeService implements ProductTypeStrategy {

    private final ProductRepository productRepository;

    protected AbstractProductTypeService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        productRepository.delete(findEntityByType(id));
    }

    @Override
    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public void updateStatus(UUID id, ProductStatus status) {
        Product product = findEntityByType(id);
        product.setStatus(status);
        productRepository.saveAndFlush(product);
    }

    protected Product findEntityByType(UUID id) {
        return productRepository.findByIdAndType(id, type()).orElseThrow(ProductNotFoundException::new);
    }
}
