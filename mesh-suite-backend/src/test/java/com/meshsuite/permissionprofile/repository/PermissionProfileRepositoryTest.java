package com.meshsuite.permissionprofile.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.permissionprofile.domain.PermissionProfile;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.UserPermissionGrant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PermissionProfileRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PermissionProfileRepository permissionProfileRepository;
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

    private PermissionProfile newProfile(UUID tenantId, String name) {
        PermissionProfile p = new PermissionProfile();
        p.setTenantId(tenantId);
        p.setName(name);
        return p;
    }

    @Test
    @Transactional
    void savesPermissionProfileWithGrants() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PermissionProfile profile = newProfile(tenant.getId(), "Gerente");
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));

        PermissionProfile saved = permissionProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        PermissionProfile reloaded = permissionProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Gerente");
        assertThat(reloaded.getIsSystem()).isFalse();
        assertThat(reloaded.getGrants()).containsExactlyInAnyOrder(
                new UserPermissionGrant(Module.CUSTOMER, Action.VIEW),
                new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
    }

    @Test
    @Transactional
    void nameMustBeUniquePerTenant() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());
        permissionProfileRepository.saveAndFlush(newProfile(tenant.getId(), "Gerente"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> permissionProfileRepository.saveAndFlush(newProfile(tenant.getId(), "Gerente")));
    }

    @Test
    @Transactional
    void sameNameAllowedAcrossDifferentTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        permissionProfileRepository.saveAndFlush(newProfile(tenantA.getId(), "Gerente"));

        setTenantContext(tenantB.getId());
        PermissionProfile saved = permissionProfileRepository.saveAndFlush(newProfile(tenantB.getId(), "Gerente"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Transactional
    void rlsHidesProfileAndGrantsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        PermissionProfile profile = newProfile(tenant.getId(), "Gerente");
        profile.getGrants().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        permissionProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long profileCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM permission_profile")
                .getSingleResult()).longValue();
        Long grantCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM permission_profile_grant")
                .getSingleResult()).longValue();

        assertThat(profileCount).isZero();
        assertThat(grantCount).isZero();
    }
}
