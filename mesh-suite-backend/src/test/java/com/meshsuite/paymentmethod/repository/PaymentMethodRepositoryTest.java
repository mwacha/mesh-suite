package com.meshsuite.paymentmethod.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.paymentmethod.domain.PaymentMethod;
import com.meshsuite.paymentmethod.domain.PaymentMethodInstallment;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PaymentMethodRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PaymentMethodRepository paymentMethodRepository;
    @Autowired EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    private PaymentMethod newPaymentMethod(UUID tenantId, String description) {
        PaymentMethod pm = new PaymentMethod();
        pm.setTenantId(tenantId);
        pm.setDescription(description);
        return pm;
    }

    @Test
    @Transactional
    void savesPaymentMethodWithInstallments() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PaymentMethod paymentMethod = newPaymentMethod(tenant.getId(), "30/60/90");
        PaymentMethodInstallment first = new PaymentMethodInstallment();
        first.setPaymentMethod(paymentMethod);
        first.setInstallmentNumber(1);
        first.setDaysDue(30);
        first.setPercentage(new BigDecimal("50.00"));
        paymentMethod.getInstallments().add(first);

        PaymentMethodInstallment second = new PaymentMethodInstallment();
        second.setPaymentMethod(paymentMethod);
        second.setInstallmentNumber(2);
        second.setDaysDue(60);
        second.setPercentage(new BigDecimal("50.00"));
        paymentMethod.getInstallments().add(second);

        PaymentMethod saved = paymentMethodRepository.saveAndFlush(paymentMethod);
        entityManager.clear();

        PaymentMethod reloaded = paymentMethodRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("30/60/90");
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getInstallments()).hasSize(2);
        assertThat(reloaded.getInstallments().get(0).getDaysDue()).isEqualTo(30);
        assertThat(reloaded.getInstallments().get(1).getDaysDue()).isEqualTo(60);
    }

    @Test
    @Transactional
    void descriptionMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        paymentMethodRepository.saveAndFlush(newPaymentMethod(tenant.getId(), "30/60/90"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> paymentMethodRepository.saveAndFlush(newPaymentMethod(tenant.getId(), "30/60/90")));
    }

    @Test
    @Transactional
    void sameDescriptionAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        paymentMethodRepository.saveAndFlush(newPaymentMethod(tenantA.getId(), "30/60/90"));

        setTenantContext(tenantB.getId());
        PaymentMethod saved = paymentMethodRepository.saveAndFlush(newPaymentMethod(tenantB.getId(), "30/60/90"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesPaymentMethodAndInstallmentsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PaymentMethod paymentMethod = newPaymentMethod(tenant.getId(), "30/60/90");
        PaymentMethodInstallment installment = new PaymentMethodInstallment();
        installment.setPaymentMethod(paymentMethod);
        installment.setInstallmentNumber(1);
        installment.setDaysDue(0);
        installment.setPercentage(new BigDecimal("100.00"));
        paymentMethod.getInstallments().add(installment);
        paymentMethodRepository.saveAndFlush(paymentMethod);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long paymentMethodCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM payment_method")
                .getSingleResult()).longValue();
        Long installmentCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM payment_method_installment")
                .getSingleResult()).longValue();

        assertThat(paymentMethodCount).isZero();
        assertThat(installmentCount).isZero();
    }
}
