package com.meshsuite.pricetable.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.pricetable.domain.PriceTable;
import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PriceTableRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PriceTableRepository priceTableRepository;
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

    private PriceTable newPriceTable(UUID tenantId, String name) {
        PriceTable t = new PriceTable();
        t.setTenantId(tenantId);
        t.setName(name);
        t.setProductSelectionMode(ProductSelectionMode.ALL_PRODUCTS);
        t.setAdjustmentMethod(AdjustmentMethod.MANUAL);
        t.setRounding(Rounding.NO_ROUNDING);
        t.setEffectiveStartDate(LocalDate.of(2026, 1, 1));
        return t;
    }

    @Test
    @Transactional
    void savesPriceTableWithDefaults() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        PriceTable saved = priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));
        entityManager.clear();

        PriceTable reloaded = priceTableRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getEffectiveStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-tp");
        Tenant tenantB = createTenant("boreal-tp");

        setTenantContext(tenantA.getId());
        priceTableRepository.saveAndFlush(newPriceTable(tenantA.getId(), "Varejo"));

        setTenantContext(tenantB.getId());
        PriceTable saved = priceTableRepository.saveAndFlush(newPriceTable(tenantB.getId(), "Varejo"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());
        priceTableRepository.saveAndFlush(newPriceTable(tenant.getId(), "Varejo"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM price_table")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
