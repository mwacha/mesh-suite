package com.meshsuite.user;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
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

    private User newUser(UUID tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setName("Marina");
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    @Transactional
    void savesAndReadsPermissionsViaElementCollection() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPermissions()).containsExactlyInAnyOrder(
                new UserPermissionGrant(Module.CUSTOMER, Action.VIEW),
                new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
    }

    @Test
    @Transactional
    void hasPermissionReturnsTrueOnlyForGrantedModuleAndAction() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.ORDER, Action.CREATE));
        User saved = userRepository.saveAndFlush(user);

        assertThat(userRepository.hasPermission(saved.getId(), Module.ORDER, Action.CREATE)).isTrue();
        assertThat(userRepository.hasPermission(saved.getId(), Module.ORDER, Action.DELETE)).isFalse();
        assertThat(userRepository.hasPermission(saved.getId(), Module.CUSTOMER, Action.CREATE)).isFalse();
    }

    @Test
    @Transactional
    void removingAGrantFromTheSetDeletesItsRow() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        User saved = userRepository.saveAndFlush(user);

        saved.getPermissions().clear();
        userRepository.saveAndFlush(saved);
        entityManager.clear();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM user_permission WHERE user_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void rlsHidesPermissionRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = newUser(tenant.getId(), "marina@aurora.com.br");
        user.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM user_permission WHERE user_id = '" + saved.getId() + "'")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
