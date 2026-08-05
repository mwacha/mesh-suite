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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    EntityManager entityManager;

    private Tenant createTenant(String codigo) {
        Tenant t = new Tenant();
        t.setCodigo(codigo);
        t.setNome(codigo);
        return tenantRepository.saveAndFlush(t);
    }

    // app_user_tenant_isolation has no explicit WITH CHECK, so its USING expression
    // also gates INSERT: writing a row requires app.tenant_id to already equal that
    // row's tenant_id. app_user_login_lookup (bypass flag) is SELECT-only and
    // doesn't help here.
    private void setTenantContext(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }

    @Test
    @Transactional
    void savesUserWithRole() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("bcrypt-hash");
        user.setRole(Role.ADMIN);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateEmailAcrossTenants() {
        Tenant tenantA = createTenant("aurora");
        Tenant tenantB = createTenant("boreal");

        setTenantContext(tenantA.getId());
        User a = new User();
        a.setTenantId(tenantA.getId());
        a.setName("Marina");
        a.setEmail("marina@confeccaoaurora.com.br");
        a.setPasswordHash("hash");
        a.setRole(Role.ADMIN);
        userRepository.saveAndFlush(a);

        setTenantContext(tenantB.getId());
        User b = new User();
        b.setTenantId(tenantB.getId());
        b.setName("Marina Outra");
        b.setEmail("marina@confeccaoaurora.com.br");
        b.setPasswordHash("hash");
        b.setRole(Role.ADMIN);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(b));
    }

    @Test
    @Transactional
    void loginBypassPolicyAllowsEmailLookupWithoutTenantContext() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@confeccaoaurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // RESET simulates no tenant context: without the bypass flag, RLS hides the row.
        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
        Long withoutBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM app_user WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withoutBypass).isZero();

        entityManager.createNativeQuery("SET LOCAL app.bypass_tenant_check = 'true'").executeUpdate();
        Long withBypass = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM app_user WHERE email = 'marina@confeccaoaurora.com.br'")
                .getSingleResult()).longValue();
        assertThat(withBypass).isEqualTo(1L);
    }

    @Test
    @Transactional
    void findsByRoleOrderedByName() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User b = new User();
        b.setTenantId(tenant.getId());
        b.setName("Bruno");
        b.setEmail("bruno@aurora.com.br");
        b.setPasswordHash("hash");
        b.setRole(Role.SALES_REP);
        userRepository.saveAndFlush(b);

        User a = new User();
        a.setTenantId(tenant.getId());
        a.setName("Ana");
        a.setEmail("ana@aurora.com.br");
        a.setPasswordHash("hash");
        a.setRole(Role.SALES_REP);
        userRepository.saveAndFlush(a);

        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setName("Carlos");
        admin.setEmail("carlos@aurora.com.br");
        admin.setPasswordHash("hash");
        admin.setRole(Role.ADMIN);
        userRepository.saveAndFlush(admin);

        var result = userRepository.findByRoleOrderByName(Role.SALES_REP);

        assertThat(result).extracting(User::getName).containsExactly("Ana", "Bruno");
    }

    @Test
    @Transactional
    void findsByRoleInAndPermissionOnlyReturnsAdminsWithPurchaseCreateGranted() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        User adminComPermissao = new User();
        adminComPermissao.setTenantId(tenant.getId());
        adminComPermissao.setName("Duda Admin");
        adminComPermissao.setEmail("duda@aurora.com.br");
        adminComPermissao.setPasswordHash("hash");
        adminComPermissao.setRole(Role.ADMIN);
        adminComPermissao.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        userRepository.saveAndFlush(adminComPermissao);

        User administrativoComPermissao = new User();
        administrativoComPermissao.setTenantId(tenant.getId());
        administrativoComPermissao.setName("Breno Administrativo");
        administrativoComPermissao.setEmail("breno@aurora.com.br");
        administrativoComPermissao.setPasswordHash("hash");
        administrativoComPermissao.setRole(Role.ADMINISTRATIVE);
        administrativoComPermissao.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        userRepository.saveAndFlush(administrativoComPermissao);

        User adminSemPermissao = new User();
        adminSemPermissao.setTenantId(tenant.getId());
        adminSemPermissao.setName("Ana Sem Permissao");
        adminSemPermissao.setEmail("ana-sem-permissao@aurora.com.br");
        adminSemPermissao.setPasswordHash("hash");
        adminSemPermissao.setRole(Role.ADMIN);
        userRepository.saveAndFlush(adminSemPermissao);

        User vendedorComPermissao = new User();
        vendedorComPermissao.setTenantId(tenant.getId());
        vendedorComPermissao.setName("Zeca Vendedor");
        vendedorComPermissao.setEmail("zeca@aurora.com.br");
        vendedorComPermissao.setPasswordHash("hash");
        vendedorComPermissao.setRole(Role.SALES_REP);
        vendedorComPermissao.getPermissions().add(new UserPermissionGrant(Module.PURCHASE, Action.CREATE));
        userRepository.saveAndFlush(vendedorComPermissao);

        var result = userRepository.findByRoleInAndPermission(
                List.of(Role.ADMIN, Role.ADMINISTRATIVE), Module.PURCHASE, Action.CREATE);

        assertThat(result).extracting(User::getName)
                .containsExactly("Breno Administrativo", "Duda Admin");
    }
}
