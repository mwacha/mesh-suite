package com.meshsuite.payable.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.payable.domain.AccountsPayable;
import com.meshsuite.payable.repository.AccountsPayableRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AccountsPayableRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired AccountsPayableRepository accountsPayableRepository;
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
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Partner fornecedor = new Partner();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setPersonType(PersonType.LEGAL_ENTITY);
        fornecedor.setDocument("11222333000144");
        fornecedor.setTradeName("Tecidos Aurora");
        fornecedor.getRoles().add(PartnerRole.SUPPLIER);
        fornecedor = partnerRepository.saveAndFlush(fornecedor);

        AccountsPayable entry = new AccountsPayable();
        entry.setTenantId(tenant.getId());
        entry.setNumber(1);
        entry.setInstallmentNumber(1);
        entry.setTotalInstallments(1);
        entry.setSupplier(fornecedor);
        entry.setAmount(new BigDecimal("100.00"));
        entry.setDueDate(LocalDate.now().plusDays(30));
        accountsPayableRepository.saveAndFlush(entry);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM accounts_payable")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
