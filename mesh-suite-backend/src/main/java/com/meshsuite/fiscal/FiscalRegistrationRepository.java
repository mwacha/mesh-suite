package com.meshsuite.fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FiscalRegistrationRepository extends JpaRepository<FiscalRegistration, UUID> {
}
