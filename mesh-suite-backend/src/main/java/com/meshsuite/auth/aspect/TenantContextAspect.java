package com.meshsuite.auth.aspect;

import com.meshsuite.shared.context.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Runs *inside* the transaction: TransactionConfig pins @EnableTransactionManagement
// to order=0 (outermost), this aspect to order=1 (one layer in), so on the way into
// a @Transactional method the tx always starts first, then this aspect's SET LOCAL,
// then the method body — every query the method body runs is tenant-scoped.
//
// COVERAGE GAP: this ordering is not currently verified by an isolated test.
// TenantIsolationTest originally exercised it incidentally -- each tenantQueryService.*
// call opened its own physical transaction through the normal AOP proxy chain (this
// aspect + the transaction advisor both firing), so a broken ordering (e.g. dropping
// @Order(1) here, or raising TransactionConfig's order past this aspect's) would
// likely have surfaced as a test failure. After TenantIsolationTest was made
// @Transactional (to fix a fixture-collision bug -- see its class-level comment),
// Spring's TransactionalTestExecutionListener opens the physical transaction directly
// via the PlatformTransactionManager, bypassing the AOP proxy chain entirely. By the
// time any tenantQueryService.* call runs, isActualTransactionActive() is already
// true for reasons unrelated to this ordering, so the test no longer exercises it. A
// future ordering regression here would not necessarily be caught by any existing
// test. Left as documented-but-uncovered rather than adding a dedicated ordering test,
// since nothing is broken today -- this is a gap for a future task to close if wanted.
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
