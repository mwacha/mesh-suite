package com.meshsuite.produto;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorEstampaRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired CorEstampaRepository corEstampaRepository;
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

    private CorEstampa novaCorEstampa(UUID tenantId, String nome) {
        CorEstampa c = new CorEstampa();
        c.setTenantId(tenantId);
        c.setNome(nome);
        c.setDataVigencia(LocalDate.of(2026, 1, 1));
        return c;
    }

    @Test
    @Transactional
    void savesCorEstampaWithDefaults() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        CorEstampa saved = corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        CorEstampa reloaded = corEstampaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
        assertThat(reloaded.getDataVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());

        corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-corest");
        Tenant tenantB = createTenant("boreal-corest");

        setTenantContext(tenantA.getId());
        corEstampaRepository.saveAndFlush(novaCorEstampa(tenantA.getId(), "Azul Marinho"));

        setTenantContext(tenantB.getId());
        CorEstampa saved = corEstampaRepository.saveAndFlush(novaCorEstampa(tenantB.getId(), "Azul Marinho"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-corest");
        setTenantContext(tenant.getId());
        corEstampaRepository.saveAndFlush(novaCorEstampa(tenant.getId(), "Azul Marinho"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM cor_estampa")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
