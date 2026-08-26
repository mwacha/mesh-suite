package com.meshsuite.product.service;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.exception.ProductNotFoundException;
import com.meshsuite.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches delete/updateStatus to whichever {@link ProductTypeStrategy} actually
 * owns the given id, regardless of {@link ProductType}. This is what lets
 * {@code DELETE /api/products/{id}} and {@code PATCH /api/products/{id}/status} work
 * uniformly for Simples/Kit/Variação without the caller needing to know the type up
 * front -- Spring collects every {@link ProductTypeStrategy} bean into the injected
 * list automatically, so a future fourth type only needs to implement the interface,
 * no change here.
 */
@Service
public class ProductTypeStrategyResolver {

    private final ProductRepository productRepository;
    private final Map<ProductType, ProductTypeStrategy> strategiesByType;

    public ProductTypeStrategyResolver(ProductRepository productRepository, List<ProductTypeStrategy> strategies) {
        this.productRepository = productRepository;
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toMap(ProductTypeStrategy::type, Function.identity()));
    }

    // @Transactional is load-bearing here, not just a convention: TenantContextAspect
    // only sets Postgres's app.tenant_id (which every RLS policy depends on) around
    // methods carrying this annotation directly (see its class comment). Without it,
    // resolveFor()'s lookup runs with no tenant context, RLS hides every row, and this
    // always 404s -- caught live against a real product, not by MockMvc tests, which
    // (via @Transactional on the *test* method) already have an active transaction
    // and tenant context by the time they call in, masking the gap.
    @Transactional
    public void delete(UUID id) {
        resolveFor(id).delete(id);
    }

    @Transactional
    public void updateStatus(UUID id, ProductStatus status) {
        resolveFor(id).updateStatus(id, status);
    }

    private ProductTypeStrategy resolveFor(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(ProductNotFoundException::new);
        // VARIATION_CHILD has no strategy of its own -- it's only ever managed through
        // its parent's update(), never as a standalone resource (same as GET/{id},
        // which SimpleProductService already hides it from).
        ProductTypeStrategy strategy = strategiesByType.get(product.getType());
        if (strategy == null) {
            throw new ProductNotFoundException();
        }
        return strategy;
    }
}
