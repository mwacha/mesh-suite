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

class TabelaPrecoRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired TabelaPrecoRepository tabelaPrecoRepository;
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

    private TabelaPreco novaTabelaPreco(UUID tenantId, String nome) {
        TabelaPreco t = new TabelaPreco();
        t.setTenantId(tenantId);
        t.setNome(nome);
        t.setModoSelecaoProdutos(ModoSelecaoProdutos.TODOS_PRODUTOS);
        t.setMetodoAjuste(MetodoAjuste.MANUAL);
        t.setArredondamento(Arredondamento.NAO_ARREDONDAR);
        t.setInicioVigencia(LocalDate.of(2026, 1, 1));
        return t;
    }

    @Test
    @Transactional
    void savesTabelaPrecoWithDefaults() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        TabelaPreco saved = tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));
        entityManager.clear();

        TabelaPreco reloaded = tabelaPrecoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAtivo()).isTrue();
        assertThat(reloaded.getInicioVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @Transactional
    void nomeMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());

        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo")));
    }

    @Test
    @Transactional
    void sameNomeAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora-tp");
        Tenant tenantB = createTenant("boreal-tp");

        setTenantContext(tenantA.getId());
        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenantA.getId(), "Varejo"));

        setTenantContext(tenantB.getId());
        TabelaPreco saved = tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenantB.getId(), "Varejo"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora-tp");
        setTenantContext(tenant.getId());
        tabelaPrecoRepository.saveAndFlush(novaTabelaPreco(tenant.getId(), "Varejo"));
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM tabela_preco")
                .getSingleResult()).longValue();

        assertThat(count).isZero();
    }
}
