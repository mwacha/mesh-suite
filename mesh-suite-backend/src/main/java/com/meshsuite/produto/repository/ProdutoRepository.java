package com.meshsuite.produto.repository;

import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.domain.enums.StatusProduto;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    long countByStatus(StatusProduto status);
    long countByCategoriaId(UUID categoriaId);
    long countByCorEstampaId(UUID corEstampaId);

    @Query("SELECT p.categoria.id AS categoriaId, COUNT(p) AS total FROM Produto p " +
            "WHERE p.categoria.id IN :categoriaIds GROUP BY p.categoria.id")
    List<CategoriaProdutoCount> countByCategoriaIdIn(@Param("categoriaIds") Collection<UUID> categoriaIds);

    @Query("SELECT p.corEstampa.id AS corEstampaId, COUNT(p) AS total FROM Produto p " +
            "WHERE p.corEstampa.id IN :corEstampaIds GROUP BY p.corEstampa.id")
    List<CorEstampaProdutoCount> countByCorEstampaIdIn(@Param("corEstampaIds") Collection<UUID> corEstampaIds);

    interface CategoriaProdutoCount {
        UUID getCategoriaId();
        Long getTotal();
    }

    interface CorEstampaProdutoCount {
        UUID getCorEstampaId();
        Long getTotal();
    }
}
