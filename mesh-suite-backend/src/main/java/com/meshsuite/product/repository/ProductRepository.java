package com.meshsuite.product.repository;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(ProductStatus status);
    long countByStatusAndType(ProductStatus status, ProductType type);
    long countByCategoryId(UUID categoryId);
    long countByColorwayId(UUID colorwayId);
    Optional<Product> findByIdAndType(UUID id, ProductType type);
    // Ordered by creation so the children come back in the same order the Tipos de
    // Variação matrix generated them. Rows saved before variation_values existed
    // carry no combination of their own, and the form falls back to matching them
    // to the matrix by position.
    List<Product> findByParentProductIdOrderByCreatedAtAscIdAsc(UUID parentProductId);
    List<Product> findByParentProductIdIn(Collection<UUID> parentProductIds);

    @Query("SELECT p.category.id AS categoryId, COUNT(p) AS total FROM Product p " +
            "WHERE p.category.id IN :categoryIds GROUP BY p.category.id")
    List<CategoryProductCount> countByCategoryIdIn(@Param("categoryIds") Collection<UUID> categoryIds);

    @Query("SELECT p.colorway.id AS colorwayId, COUNT(p) AS total FROM Product p " +
            "WHERE p.colorway.id IN :colorwayIds GROUP BY p.colorway.id")
    List<ColorwayProductCount> countByColorwayIdIn(@Param("colorwayIds") Collection<UUID> colorwayIds);

    interface CategoryProductCount {
        UUID getCategoryId();
        Long getTotal();
    }

    interface ColorwayProductCount {
        UUID getColorwayId();
        Long getTotal();
    }
}
