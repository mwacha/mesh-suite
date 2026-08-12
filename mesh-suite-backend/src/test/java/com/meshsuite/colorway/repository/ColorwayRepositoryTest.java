package com.meshsuite.colorway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class ColorwayRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ColorwayRepository colorwayRepository;
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

    private Colorway novaColorway(UUID tenantId, String nome) {
        Colorway c = new Colorway();
        c.setTenantId(tenantId);
        c.setName(nome);
        c.setEffectiveDate(LocalDate.of(2026, 1, 1));
        return c;
    }

    @Test
    @Transactional
    void savesColorwayWithDefaults() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        Colorway saved = colorwayRepository.saveAndFlush(novaColorway(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        Colorway reloaded = colorwayRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        colorwayRepository.saveAndFlush(novaColorway(tenant.getId(), "Azul Marinho"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> colorwayRepository.saveAndFlush(novaColorway(tenant.getId(), "Azul Marinho")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-corest");
        Tenant tenantB = createTenant("boreal-corest");

        setTenantContext(tenantA.getId());
        colorwayRepository.saveAndFlush(novaColorway(tenantA.getId(), "Azul Marinho"));

        setTenantContext(tenantB.getId());
        Colorway saved = colorwayRepository.saveAndFlush(novaColorway(tenantB.getId(), "Azul Marinho"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());
        colorwayRepository.saveAndFlush(novaColorway(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM colorway")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
