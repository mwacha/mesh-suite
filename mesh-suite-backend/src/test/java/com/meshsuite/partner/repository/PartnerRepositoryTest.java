package com.meshsuite.partner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.PartnerContact;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PartnerRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerRepository partnerRepository;
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

    private Partner newPartner(UUID tenantId, String document) {
        Partner p = new Partner();
        p.setTenantId(tenantId);
        p.setPersonType(PersonType.LEGAL_ENTITY);
        p.setDocument(document);
        p.setTradeName("Mercado Silva");
        p.setLegalName("Mercado Silva Ltda");
        p.setRoles(Set.of(PartnerRole.CUSTOMER));
        return p;
    }

    @Test
    @Transactional
    void savesPartnerWithRolesAndContacts() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Partner partner = newPartner(tenant.getId(), "11222333000144");
        PartnerContact contact = new PartnerContact();
        contact.setPartner(partner);
        contact.setName("Ana Souza");
        contact.setJobTitle("Financeiro");
        partner.getContacts().add(contact);

        Partner saved = partnerRepository.saveAndFlush(partner);
        entityManager.clear();

        Partner reloaded = partnerRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).containsExactly(PartnerRole.CUSTOMER);
        assertThat(reloaded.getContacts()).hasSize(1);
        assertThat(reloaded.getContacts().get(0).getName()).isEqualTo("Ana Souza");
        assertThat(reloaded.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }

    @Test
    @Transactional
    void documentMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        partnerRepository.saveAndFlush(newPartner(tenant.getId(), "11222333000144"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> partnerRepository.saveAndFlush(newPartner(tenant.getId(), "11222333000144")));
    }

    @Test
    @Transactional
    void sameDocumentAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        partnerRepository.saveAndFlush(newPartner(tenantA.getId(), "11222333000144"));

        setTenantContext(tenantB.getId());
        Partner saved = partnerRepository.saveAndFlush(newPartner(tenantB.getId(), "11222333000144"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesPartnerAndChildRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Partner partner = newPartner(tenant.getId(), "11222333000144");
        PartnerContact contact = new PartnerContact();
        contact.setPartner(partner);
        contact.setName("Ana Souza");
        partner.getContacts().add(contact);
        partnerRepository.saveAndFlush(partner);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long partnerCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner")
                .getSingleResult()).longValue();
        Long roleCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner_role")
                .getSingleResult()).longValue();
        Long contactCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM partner_contact")
                .getSingleResult()).longValue();

        assertThat(partnerCount).isZero();
        assertThat(roleCount).isZero();
        assertThat(contactCount).isZero();
    }
}
