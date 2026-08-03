package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserPermissionGrant;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PermissionAspectTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;
    @Autowired ProbeService probeService;

    private void authenticateAs(UUID userId) {
        var principal = new AuthContextService.Context(userId, null, "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void allowsWhenPermissionGranted() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("aurora");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        User saved = userRepository.saveAndFlush(user);

        TenantContext.set(tenant.getId());
        authenticateAs(saved.getId());
        try {
            assertThat(probeService.probe()).isEqualTo("ok");
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deniesWhenPermissionNotGranted() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("boreal");
        tenant.setNome("boreal");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Carlos");
        user.setEmail("carlos@boreal.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        User saved = userRepository.saveAndFlush(user);

        TenantContext.set(tenant.getId());
        authenticateAs(saved.getId());
        try {
            assertThrows(PermissionDeniedException.class, () -> probeService.probe());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
