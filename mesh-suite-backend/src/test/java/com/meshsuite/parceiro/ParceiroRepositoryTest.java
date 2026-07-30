package com.meshsuite.parceiro;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParceiroRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ParceiroRepository parceiroRepository;
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

    private Parceiro novoParceiro(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.setRazaoSocial("Mercado Silva Ltda");
        p.setPapeis(Set.of(PapelParceiro.CLIENTE));
        return p;
    }

    @Test
    @Transactional
    void savesParceiroWithPapeisAndContatos() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Parceiro parceiro = novoParceiro(tenant.getId(), "11222333000144");
        ParceiroContato contato = new ParceiroContato();
        contato.setParceiro(parceiro);
        contato.setNome("Ana Souza");
        contato.setCargo("Financeiro");
        parceiro.getContatos().add(contato);

        Parceiro saved = parceiroRepository.saveAndFlush(parceiro);
        entityManager.clear();

        Parceiro reloaded = parceiroRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPapeis()).containsExactly(PapelParceiro.CLIENTE);
        assertThat(reloaded.getContatos()).hasSize(1);
        assertThat(reloaded.getContatos().get(0).getNome()).isEqualTo("Ana Souza");
        assertThat(reloaded.getStatus()).isEqualTo(StatusParceiro.ATIVO);
    }

    @Test
    @Transactional
    void documentoMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        parceiroRepository.saveAndFlush(novoParceiro(tenant.getId(), "11222333000144"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> parceiroRepository.saveAndFlush(novoParceiro(tenant.getId(), "11222333000144")));
    }

    @Test
    @Transactional
    void sameDocumentoAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        parceiroRepository.saveAndFlush(novoParceiro(tenantA.getId(), "11222333000144"));

        setTenantContext(tenantB.getId());
        Parceiro saved = parceiroRepository.saveAndFlush(novoParceiro(tenantB.getId(), "11222333000144"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesParceiroAndChildRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Parceiro parceiro = novoParceiro(tenant.getId(), "11222333000144");
        ParceiroContato contato = new ParceiroContato();
        contato.setParceiro(parceiro);
        contato.setNome("Ana Souza");
        parceiro.getContatos().add(contato);
        parceiroRepository.saveAndFlush(parceiro);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long parceiroCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro")
                .getSingleResult()).longValue();
        Long papelCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro_papel")
                .getSingleResult()).longValue();
        Long contatoCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM parceiro_contato")
                .getSingleResult()).longValue();

        assertThat(parceiroCount).isZero();
        assertThat(papelCount).isZero();
        assertThat(contatoCount).isZero();
    }
}
