package com.meshsuite.auth.repository;

import com.meshsuite.auth.domain.TenantSignupToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TenantSignupTokenRepository extends JpaRepository<TenantSignupToken, UUID> {
    Optional<TenantSignupToken> findByTokenHash(String tokenHash);

    // @Transactional directly on this method is required for a @Modifying query to
    // run outside a caller-provided transaction (standard Spring Data JPA pattern).
    // clearAutomatically = true: a JPQL bulk UPDATE bypasses the persistence
    // context, so without this, any TenantSignupToken already loaded/saved earlier
    // in the same persistence context (e.g. the token from an earlier signup call
    // within the same transaction/session) would keep returning its stale
    // pre-update usadoEm value on a later find, even though the DB row changed.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE TenantSignupToken t SET t.usadoEm = CURRENT_TIMESTAMP WHERE t.tenantId = :tenantId AND t.usadoEm IS NULL")
    int invalidateAllForTenant(@Param("tenantId") UUID tenantId);
}
