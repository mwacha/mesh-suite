package com.meshsuite.empresa.repository;

import com.meshsuite.empresa.domain.Empresa;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    List<Empresa> findByTenantId(UUID tenantId);
}
