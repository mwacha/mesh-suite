package com.meshsuite.brand.repository;

import com.meshsuite.brand.domain.Brand;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BrandRepository extends JpaRepository<Brand, UUID>, JpaSpecificationExecutor<Brand> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
    long countByActive(boolean active);
}
