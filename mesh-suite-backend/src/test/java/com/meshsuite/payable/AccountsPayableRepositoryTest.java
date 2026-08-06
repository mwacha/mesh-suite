package com.meshsuite.payable;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.parceiro.TipoPessoa;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsPayableRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
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

        Parceiro fornecedor = new Parceiro();
        fornecedor.setTenantId(tenant.getId());
        fornecedor.setTipoPessoa(TipoPessoa.JURIDICA);
        fornecedor.setDocumento("11222333000144");
        fornecedor.setNomeFantasia("Tecidos Aurora");
        fornecedor.getPapeis().add(PapelParceiro.FORNECEDOR);
        fornecedor = parceiroRepository.saveAndFlush(fornecedor);

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
