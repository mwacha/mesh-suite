package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);

    // Used when updating a VARIATION_PARENT's variantes: those rows are about
    // to be re-merged (see ProdutoVariacaoService.aplicarVariantes), so an
    // unchanged variant's own SKU must not trip the duplicate check against
    // itself. Excludes every existing child of parentId rather than a single
    // id, since the incoming SKU could match any of that parent's *other*
    // current variants too (a swap between two combinações in the same
    // request) -- those are being replaced in the same operation, not a real
    // conflict.
    @Query("SELECT COUNT(p) > 0 FROM Produto p WHERE p.sku = :sku "
            + "AND (p.parent IS NULL OR p.parent.id <> :parentId)")
    boolean existsBySkuOutsideParent(@Param("sku") String sku, @Param("parentId") UUID parentId);

    // Excludes VARIATION_CHILD rows (they're implementation detail lines of
    // their VARIATION_PARENT, never a standalone catalog entry) -- same
    // "root of the catalog" filter as ProdutoSpecifications.raizesDoCatalogo(),
    // just expressed as a derived query since this isn't Specification-based.
    long countByStatusAndParentIsNull(StatusProduto status);
}
