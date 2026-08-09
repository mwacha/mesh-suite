package com.meshsuite.payable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.payable.domain.enums.AccountsPayableStatus;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
import com.meshsuite.payable.exception.AccountsPayableValidationException;
import com.meshsuite.payable.service.AccountsPayableService;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class AccountsPayableServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ParceiroRepository parceiroRepository;
    @Autowired AccountsPayableService accountsPayableService;
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
        caller.setName("Test Caller");
        caller.setEmail("caller-" + UUID.randomUUID() + "@" + codigo + ".com.br");
        caller.setPasswordHash("hash");
        caller.setRole(Role.ADMINISTRATIVE);
        caller.setProfile(Profile.ADMIN);
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(caller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private UUID criarFornecedor(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Tecidos Aurora");
        p.getPapeis().add(PapelParceiro.FORNECEDOR);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    private UUID criarCliente(UUID tenantId, String documento) {
        Parceiro p = new Parceiro();
        p.setTenantId(tenantId);
        p.setTipoPessoa(TipoPessoa.JURIDICA);
        p.setDocumento(documento);
        p.setNomeFantasia("Mercado Silva");
        p.getPapeis().add(PapelParceiro.CLIENTE);
        return parceiroRepository.saveAndFlush(p).getId();
    }

    @Test
    void createsInstallmentsWithSequentialNumberingAndCorrectParcela() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var installments = List.of(
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(30)),
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(60)),
                new AccountsPayableInstallmentInput(new BigDecimal("100.00"), LocalDate.now().plusDays(90)));

        var created = accountsPayableService.createInstallments(tenantId, supplierId, null, installments);

        assertThat(created).hasSize(3);
        assertThat(created.get(0).number()).isEqualTo(1);
        assertThat(created.get(1).number()).isEqualTo(2);
        assertThat(created.get(2).number()).isEqualTo(3);
        assertThat(created.get(0).installmentNumber()).isEqualTo(1);
        assertThat(created.get(0).totalInstallments()).isEqualTo(3);
        assertThat(created.get(2).installmentNumber()).isEqualTo(3);
        assertThat(created.get(0).status()).isEqualTo(AccountsPayableStatus.OPEN);
        assertThat(created.get(0).supplierName()).isEqualTo("Tecidos Aurora");
    }

    @Test
    void rejectsSupplierWithoutFornecedorPapel() {
        UUID tenantId = setUpTenant("aurora");
        UUID clienteId = criarCliente(tenantId, "11222333000144");
        var installments = List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now()));

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.createInstallments(tenantId, clienteId, null, installments));
    }

    @Test
    void marksAsPaidFromOpen() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        var paid = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        assertThat(paid.status()).isEqualTo(AccountsPayableStatus.PAID);
        assertThat(paid.paymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void reversesFromPaid() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        var reverted = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);

        assertThat(reverted.status()).isEqualTo(AccountsPayableStatus.OPEN);
        assertThat(reverted.paymentDate()).isNull();
    }

    @Test
    void rejectsMarkingAsPaidTwice() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID));
    }

    @Test
    void rejectsReversingAnOpenEntry() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        assertThrows(AccountsPayableValidationException.class,
                () -> accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN));
    }

    @Test
    void reversalCanHappenMoreThanOnce() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));

        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);
        var revertedAgain = accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.OPEN);

        assertThat(revertedAgain.status()).isEqualTo(AccountsPayableStatus.OPEN);
    }

    @Test
    void listFiltersByStatus() {
        UUID tenantId = setUpTenant("aurora");
        UUID supplierId = criarFornecedor(tenantId, "11222333000144");
        var created = accountsPayableService.createInstallments(tenantId, supplierId, null,
                List.of(new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now()),
                        new AccountsPayableInstallmentInput(BigDecimal.TEN, LocalDate.now())));
        accountsPayableService.updateStatus(created.get(0).id(), AccountsPayableStatus.PAID);

        var openPage = accountsPayableService.list(AccountsPayableStatus.OPEN, PageRequest.of(0, 10));
        var paidPage = accountsPayableService.list(AccountsPayableStatus.PAID, PageRequest.of(0, 10));

        assertThat(openPage.getTotalElements()).isEqualTo(1);
        assertThat(paidPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    void deniesListingWhenCallerLacksPayableViewPermission() {
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
        noPerms.setRole(Role.ADMINISTRATIVE);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> accountsPayableService.list(null, PageRequest.of(0, 10)));
    }
}
