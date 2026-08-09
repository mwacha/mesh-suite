package com.meshsuite.fiscal.repository;

import com.meshsuite.fiscal.domain.FiscalRegistration;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalRegistrationRepository extends JpaRepository<FiscalRegistration, UUID> {
}
