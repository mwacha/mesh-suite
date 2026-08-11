package com.meshsuite.partner.repository;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {
    boolean existsByDocument(String document);
    boolean existsByDocumentAndIdNot(String document, UUID id);
    long countByStatus(PartnerStatus status);
    long countByStatusAndRolesContaining(PartnerStatus status, PartnerRole role);
}
