package com.meshsuite.permissionprofile.repository;

import com.meshsuite.permissionprofile.domain.PermissionProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PermissionProfileRepository
        extends JpaRepository<PermissionProfile, UUID>, JpaSpecificationExecutor<PermissionProfile> {
    boolean existsByTenantIdAndName(UUID tenantId, String name);
    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
    long countByTenantId(UUID tenantId);
}
