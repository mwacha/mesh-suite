package com.meshsuite.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthContextService {

    private final EntityManager entityManager;

    public AuthContextService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record Context(UUID usuarioId, UUID tenantId, String papel) {
    }

    // Callers must set TenantContext.set(tenantId) *before* invoking this method,
    // so TenantContextAspect can issue SET LOCAL before this query runs.
    @Transactional(readOnly = true)
    public boolean usuarioETenantAtivos(UUID tenantId, UUID usuarioId) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT u.ativo, t.ativo FROM usuario u JOIN tenant t ON t.id = u.tenant_id " +
                                    "WHERE u.id = :usuarioId AND u.tenant_id = :tenantId")
                    .setParameter("usuarioId", usuarioId)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return (boolean) row[0] && (boolean) row[1];
        } catch (NoResultException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String nomeDoUsuario(UUID usuarioId) {
        return entityManager.createQuery(
                        "SELECT u.nome FROM Usuario u WHERE u.id = :id", String.class)
                .setParameter("id", usuarioId)
                .getSingleResult();
    }
}
