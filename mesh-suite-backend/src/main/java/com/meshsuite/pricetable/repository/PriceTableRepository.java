package com.meshsuite.pricetable.repository;

import com.meshsuite.pricetable.domain.PriceTable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PriceTableRepository extends JpaRepository<PriceTable, UUID>, JpaSpecificationExecutor<PriceTable> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
    long countByActive(boolean active);
}
