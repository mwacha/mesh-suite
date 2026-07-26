package com.meshsuite.auth;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Runs *inside* the transaction: TransactionConfig pins @EnableTransactionManagement
// to order=0 (outermost), this aspect to order=1 (one layer in), so on the way into
// a @Transactional method the tx always starts first, then this aspect's SET LOCAL,
// then the method body — every query the method body runs is tenant-scoped.
@Aspect
@Component
@Order(1)
public class TenantContextAspect {

    private final EntityManager entityManager;

    public TenantContextAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantContext(ProceedingJoinPoint pjp) throws Throwable {
        UUID tenantId = TenantContext.get();
        if (tenantId != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            // Postgres's SET LOCAL doesn't accept bind parameters (it isn't a DML
            // statement). Concatenating is safe here because tenantId is a
            // java.util.UUID, not raw user input — toString() always produces the
            // fixed 36-char hex-and-dash format, nothing else.
            entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'")
                    .executeUpdate();
        }
        return pjp.proceed();
    }
}
