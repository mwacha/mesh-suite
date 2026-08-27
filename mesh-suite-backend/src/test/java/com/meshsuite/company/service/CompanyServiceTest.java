package com.meshsuite.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.company.dto.CompanyRequest;
import com.meshsuite.company.exception.CompanyIsLastForTenantException;
import com.meshsuite.company.exception.CompanyNotFoundException;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

class CompanyServiceTest extends AbstractIntegrationTest {

    @Autowired CompanyService companyService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

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
        caller.setRole(Role.ADMIN);
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

    private CompanyRequest request(String legalName, String cnpj) {
        return new CompanyRequest(legalName, cnpj, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    @Transactional
    void createsAndRetrievesCompany() {
        setUpTenant("aurora-empresa");

        var criada = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));

        var buscada = companyService.findById(criada.id());
        assertThat(buscada.legalName()).isEqualTo("Confecção Aurora Ltda");
        assertThat(buscada.cnpj()).isEqualTo("11222333000144");
        assertThat(buscada.active()).isTrue();
    }

    @Test
    @Transactional
    void rejectsDuplicateCnpjOnCreate() {
        setUpTenant("aurora-empresa");
        companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));

        assertThatThrownBy(() -> companyService.create(TenantContext.get(),
                request("Outra Filial Ltda", "11222333000144")))
                .isInstanceOf(DuplicateCnpjException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateCnpjOnUpdateAgainstAnotherCompany() {
        setUpTenant("aurora-empresa");
        companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));
        var outra = companyService.create(TenantContext.get(), request("Filial Sul Ltda", "22333444000155"));

        assertThatThrownBy(() -> companyService.update(outra.id(), request("Filial Sul Ltda", "11222333000144")))
                .isInstanceOf(DuplicateCnpjException.class);
    }

    @Test
    @Transactional
    void allowsUpdatingACompanyWithoutChangingItsOwnCnpj() {
        setUpTenant("aurora-empresa");
        var criada = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));

        var atualizada = companyService.update(criada.id(), request("Confecção Aurora S.A.", "11222333000144"));

        assertThat(atualizada.legalName()).isEqualTo("Confecção Aurora S.A.");
    }

    @Test
    @Transactional
    void updatesStatus() {
        setUpTenant("aurora-empresa");
        var criada = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));

        var inativada = companyService.updateStatus(criada.id(), false);

        assertThat(inativada.active()).isFalse();
    }

    @Test
    @Transactional
    void deletesCompanyWhenMoreThanOneExistsForTenant() {
        setUpTenant("aurora-empresa");
        var matriz = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));
        var filial = companyService.create(TenantContext.get(), request("Confecção Aurora Filial", "22333444000155"));

        companyService.delete(filial.id());

        assertThatThrownBy(() -> companyService.findById(filial.id()))
                .isInstanceOf(CompanyNotFoundException.class);
        assertThat(companyService.findById(matriz.id())).isNotNull();
    }

    @Test
    @Transactional
    void rejectsDeletingTheLastCompanyForTenant() {
        setUpTenant("aurora-empresa");
        var unica = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));

        assertThatThrownBy(() -> companyService.delete(unica.id()))
                .isInstanceOf(CompanyIsLastForTenantException.class);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-empresa");
        var ativa = companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));
        var inativa = companyService.create(TenantContext.get(), request("Confecção Descontinuada", "22333444000155"));
        companyService.updateStatus(inativa.id(), false);

        var ativas = companyService.list(null, true, null, null, PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("id").containsExactly(ativa.id());
    }

    @Test
    @Transactional
    void countsTotalActiveAndInactiveCompanies() {
        setUpTenant("aurora-empresa");
        companyService.create(TenantContext.get(), request("Confecção Aurora Ltda", "11222333000144"));
        var descontinuada = companyService.create(TenantContext.get(),
                request("Confecção Descontinuada", "22333444000155"));
        companyService.updateStatus(descontinuada.id(), false);

        var contagens = companyService.counts();

        assertThat(contagens.total()).isEqualTo(2L);
        assertThat(contagens.active()).isEqualTo(1L);
        assertThat(contagens.inactive()).isEqualTo(1L);
    }
}
