package com.meshsuite.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.dto.PermissionDto;
import com.meshsuite.user.dto.UserRequest;
import com.meshsuite.user.exception.EmailAlreadyExistsException;
import com.meshsuite.user.exception.UserValidationException;
import com.meshsuite.user.repository.UserRepository;
import com.meshsuite.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;
    @Autowired EntityManager entityManager;
    @Autowired com.meshsuite.permissionprofile.repository.PermissionProfileRepository permissionProfileRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID setUpTenant(String codigo) {
        Tenant tenant = new Tenant();
        tenant.setCodigo(codigo);
        tenant.setNome(codigo);
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());
        return tenant.getId();
    }

    private void authenticateAsFullAdmin(UUID tenantId) {
        User admin = new User();
        admin.setTenantId(tenantId);
        admin.setName("Admin Caller");
        admin.setEmail("admin-caller-" + UUID.randomUUID() + "@aurora.com.br");
        admin.setPasswordHash("hash");
        admin.setRole(Role.ADMINISTRATIVE);
        admin.setProfile(Profile.ADMIN);
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        admin.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        User saved = userRepository.saveAndFlush(admin);
        authenticateAs(saved.getId());
    }

    private void authenticateAsNoPermissions(UUID tenantId) {
        User noPerms = new User();
        noPerms.setTenantId(tenantId);
        noPerms.setName("No Permissions Caller");
        noPerms.setEmail("no-perms-" + UUID.randomUUID() + "@aurora.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);
        authenticateAs(saved.getId());
    }

    private void authenticateAs(UUID userId) {
        var principal = new AuthContextService.Context(userId, null, "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UserRequest request(String email, String password, String confirmPassword, List<PermissionDto> permissions) {
        return new UserRequest("Marina", email, "(11) 99999-9999", Role.ADMINISTRATIVE, Profile.ADMIN, true,
                password, confirmPassword, permissions, null);
    }

    private UserRequest requestWithProfile(String email, String password, String confirmPassword,
                                            List<PermissionDto> permissions, UUID permissionProfileId) {
        return new UserRequest("Marina", email, "(11) 99999-9999", Role.ADMINISTRATIVE, Profile.ADMIN, true,
                password, confirmPassword, permissions, permissionProfileId);
    }

    @Test
    void createsAndRetrievesUser() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThat(criado.name()).isEqualTo("Marina");
        assertThat(criado.active()).isTrue();

        var buscado = userService.findById(criado.id());
        assertThat(buscado.email()).isEqualTo("marina@aurora.com.br");
    }

    @Test
    void rejectsDuplicateEmailInSameTenant() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThrows(EmailAlreadyExistsException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "outraSenha1", "outraSenha1", List.of())));
    }

    @Test
    void rejectsCreateWithoutPassword() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", null, null, List.of())));
    }

    @Test
    void rejectsMismatchedPasswordConfirmation() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "outraSenha1", List.of())));
    }

    @Test
    void rejectsPasswordWithoutDigits() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        assertThrows(UserValidationException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "somenteletras", "somenteletras", List.of())));
    }

    @Test
    void updateWithBlankPasswordKeepsExistingHash() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));
        String originalHash = userRepository.findById(criado.id()).orElseThrow().getPasswordHash();

        userService.update(criado.id(), request("marina@aurora.com.br", "", "", List.of()));

        assertThat(userRepository.findById(criado.id()).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void updateReplacesThePermissionList() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234",
                List.of(new PermissionDto(Module.CUSTOMER, Action.VIEW))));

        var atualizado = userService.update(criado.id(), request("marina@aurora.com.br", "", "",
                List.of(new PermissionDto(Module.PRODUCT, Action.EDIT), new PermissionDto(Module.ORDER, Action.VIEW))));

        assertThat(atualizado.permissions()).containsExactlyInAnyOrder(
                new PermissionDto(Module.PRODUCT, Action.EDIT), new PermissionDto(Module.ORDER, Action.VIEW));
    }

    @Test
    void updateStatusTogglesActive() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        var atualizado = userService.updateStatus(criado.id(), false);

        assertThat(atualizado.active()).isFalse();
    }

    @Test
    void countsByActiveStatus() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        var a = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));
        userService.create(tenantId, request("carlos@aurora.com.br", "senha1234", "senha1234", List.of()));
        userService.updateStatus(a.id(), false);

        // Includes the admin-caller fixture itself, created active in authenticateAsFullAdmin.
        var counts = userService.counts();

        assertThat(counts.total()).isEqualTo(3);
        assertThat(counts.active()).isEqualTo(2);
        assertThat(counts.inactive()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        var pagina = userService.list("marina", null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).email()).isEqualTo("marina@aurora.com.br");
    }

    @Test
    void deniesCreateWhenCallerLacksPermission() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsNoPermissions(tenantId);

        assertThrows(PermissionDeniedException.class,
                () -> userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of())));
    }

    @Test
    void linksPermissionProfileToUser() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);
        com.meshsuite.permissionprofile.domain.PermissionProfile perfil =
                new com.meshsuite.permissionprofile.domain.PermissionProfile();
        perfil.setTenantId(tenantId);
        perfil.setName("Financeiro");
        permissionProfileRepository.saveAndFlush(perfil);

        var criado = userService.create(tenantId,
                requestWithProfile("marina@aurora.com.br", "senha1234", "senha1234", List.of(), perfil.getId()));

        assertThat(criado.permissionProfileId()).isEqualTo(perfil.getId());
        assertThat(criado.permissionProfileName()).isEqualTo("Financeiro");
    }

    @Test
    void createsUserWithoutPermissionProfile() {
        UUID tenantId = setUpTenant("aurora");
        authenticateAsFullAdmin(tenantId);

        var criado = userService.create(tenantId, request("marina@aurora.com.br", "senha1234", "senha1234", List.of()));

        assertThat(criado.permissionProfileId()).isNull();
        assertThat(criado.permissionProfileName()).isNull();
    }
}
