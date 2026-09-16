package com.meshsuite.auth.repository;

import com.meshsuite.auth.domain.TenantSignupToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSignupTokenRepository extends JpaRepository<TenantSignupToken, UUID> {
    Optional<TenantSignupToken> findByTokenHash(String tokenHash);
}
