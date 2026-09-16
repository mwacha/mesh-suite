package com.meshsuite.company.repository;

import com.meshsuite.company.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {
    List<Company> findByTenantId(UUID tenantId);
    boolean existsByCnpj(String cnpj);
    boolean existsByCnpjAndIdNot(String cnpj, UUID id);
    Optional<Company> findByCnpj(String cnpj);
    long countByActive(boolean active);
    long countByTenantId(UUID tenantId);
}
