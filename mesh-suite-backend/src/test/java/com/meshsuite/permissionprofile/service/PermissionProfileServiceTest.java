package com.meshsuite.permissionprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException;
import com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException;
import com.meshsuite.permissionprofile.exception.PermissionProfileValidationException;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.dto.PermissionDto;
import com.meshsuite.user.repository.UserRepository;
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
class PermissionProfileServiceTest extends AbstractIntegrationTest {

    @Autowired PermissionProfileService permissionProfileService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

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

        User caller = new User();
        caller.setTenantId(tenant.getId());
        caller.setName("Marina");
        caller.setEmail(codigo + "@aurora.com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.USER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private com.meshsuite.permissionprofile.dto.PermissionProfileRequest request(String name, List<PermissionDto> grants) {
        return new com.meshsuite.permissionprofile.dto.PermissionProfileRequest(name, "Descrição de teste", grants);
    }

    @Test
    void createsAndRetrievesProfileWithGrants() {
        setUpTenant("aurora");

        var criado = permissionProfileService.create(TenantContext.get(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.VIEW))));

        var buscado = permissionProfileService.findById(criado.id());
        assertThat(buscado.name()).isEqualTo("Financeiro");
        assertThat(buscado.isSystem()).isFalse();
        assertThat(buscado.grants()).containsExactly(new PermissionDto(Module.PAYABLE, Action.VIEW));
    }

    @Test
    void listSeedsTheFourDefaultProfilesOnFirstCall() {
        setUpTenant("aurora");

        var pagina = permissionProfileService.list(null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting("name")
                .containsExactlyInAnyOrder("Admin", "Gerente", "Vendedor", "Visualizador");
        assertThat(pagina.getContent()).allMatch(com.meshsuite.permissionprofile.dto.PermissionProfileSummaryResponse::isSystem);
    }

    @Test
    void listDoesNotReseedOnSecondCall() {
        setUpTenant("aurora");
        permissionProfileService.list(null, PageRequest.of(0, 10));

        var segunda = permissionProfileService.list(null, PageRequest.of(0, 10));

        assertThat(segunda.getTotalElements()).isEqualTo(4);
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora");
        permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of()));

        assertThatThrownBy(() -> permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of())))
                .isInstanceOf(DuplicatePermissionProfileNameException.class);
    }

    @Test
    void updateReplacesTheWholeGrantList() {
        setUpTenant("aurora");
        var criado = permissionProfileService.create(TenantContext.get(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.VIEW))));

        var atualizado = permissionProfileService.update(criado.id(),
                request("Financeiro", List.of(new PermissionDto(Module.PAYABLE, Action.EDIT), new PermissionDto(Module.SALE, Action.VIEW))));

        assertThat(atualizado.grants()).containsExactlyInAnyOrder(
                new PermissionDto(Module.PAYABLE, Action.EDIT), new PermissionDto(Module.SALE, Action.VIEW));
    }

    @Test
    void allowsEditingASystemProfilesGrants() {
        setUpTenant("aurora");
        permissionProfileService.list(null, PageRequest.of(0, 10));
        var admin = permissionProfileService.list(null, PageRequest.of(0, 10)).getContent().stream()
                .filter(p -> p.name().equals("Admin")).findFirst().orElseThrow();

        var atualizado = permissionProfileService.update(admin.id(),
                request("Admin", List.of(new PermissionDto(Module.CUSTOMER, Action.VIEW))));

        assertThat(atualizado.grants()).containsExactly(new PermissionDto(Module.CUSTOMER, Action.VIEW));
    }

    @Test
    void rejectsDeletingASystemProfile() {
        setUpTenant("aurora");
        var admin = permissionProfileService.list(null, PageRequest.of(0, 10)).getContent().stream()
                .filter(p -> p.name().equals("Admin")).findFirst().orElseThrow();

        assertThatThrownBy(() -> permissionProfileService.delete(admin.id()))
                .isInstanceOf(PermissionProfileValidationException.class);
    }

    @Test
    void deletesACustomProfile() {
        setUpTenant("aurora");
        var criado = permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of()));

        permissionProfileService.delete(criado.id());

        assertThatThrownBy(() -> permissionProfileService.findById(criado.id()))
                .isInstanceOf(PermissionProfileNotFoundException.class);
    }

    @Test
    void deniesCreateWhenCallerLacksUserCreatePermission() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao");
        tenant.setNome("sem-permissao");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User noPerms = new User();
        noPerms.setTenantId(tenant.getId());
        noPerms.setName("No Permissions");
        noPerms.setEmail("no-perms@sem-permissao.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> permissionProfileService.create(TenantContext.get(), request("Financeiro", List.of())))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
