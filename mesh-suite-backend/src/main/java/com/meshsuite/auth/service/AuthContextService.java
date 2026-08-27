package com.meshsuite.auth.service;

import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.shared.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthContextService {

    private final EntityManager entityManager;
    private final CompanyRepository companyRepository;

    public AuthContextService(EntityManager entityManager, CompanyRepository companyRepository) {
        this.entityManager = entityManager;
        this.companyRepository = companyRepository;
    }

    public record Context(UUID usuarioId, UUID tenantId, String papel) {
    }

    // Callers must set TenantContext.set(tenantId) *before* invoking this method,
    // so TenantContextAspect can issue SET LOCAL before this query runs.
    @Transactional(readOnly = true)
    public boolean userAndTenantActive(UUID tenantId, UUID userId) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT u.active, t.ativo FROM app_user u JOIN tenant t ON t.id = u.tenant_id " +
                                    "WHERE u.id = :userId AND u.tenant_id = :tenantId")
                    .setParameter("userId", userId)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return (boolean) row[0] && (boolean) row[1];
        } catch (NoResultException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String userName(UUID userId) {
        return entityManager.createQuery(
                        "SELECT u.name FROM User u WHERE u.id = :id", String.class)
                .setParameter("id", userId)
                .getSingleResult();
    }

    // Called with TenantContext already set by JwtAuthenticationFilter for the
    // current request, so TenantContextAspect scopes this to the right tenant --
    // same "matriz, as default" pick as AuthService.loadTenantAndCompany at login.
    @Transactional(readOnly = true)
    public String companyName(UUID tenantId) {
        return companyRepository.findByTenantId(tenantId).stream()
                .findFirst()
                .map(AuthContextService::displayName)
                .orElse(null);
    }

    private static String displayName(Company company) {
        String tradeName = company.getTradeName();
        return tradeName != null && !tradeName.isBlank() ? tradeName : company.getLegalName();
    }
}
