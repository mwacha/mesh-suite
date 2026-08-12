package com.meshsuite.product.repository;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(ProductStatus status);
    long countByCategoryId(UUID categoryId);
    long countByColorwayId(UUID colorwayId);

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
