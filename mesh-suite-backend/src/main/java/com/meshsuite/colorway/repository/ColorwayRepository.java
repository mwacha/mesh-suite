package com.meshsuite.colorway.repository;

import com.meshsuite.colorway.domain.Colorway;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ColorwayRepository extends JpaRepository<Colorway, UUID>, JpaSpecificationExecutor<Colorway> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
    long countByActive(boolean active);
}
