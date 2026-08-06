package com.meshsuite.fiscal;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalRegistrationRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired FiscalRegistrationRepository fiscalRegistrationRepository;
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

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-fiscal");
        setTenantContext(tenant.getId());

        FiscalRegistration registration = new FiscalRegistration();
        registration.setTenantId(tenant.getId());
        registration.setDescription("Compra dentro do estado");
        registration.setCfop("1102");
        registration.setIcmsCst("000");
        registration.setIcmsRate(new BigDecimal("18.00"));
        registration.setIpiRate(new BigDecimal("5.00"));
        registration.setPisRate(new BigDecimal("1.65"));
        registration.setCofinsRate(new BigDecimal("7.60"));
        fiscalRegistrationRepository.saveAndFlush(registration);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM fiscal_registration")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
