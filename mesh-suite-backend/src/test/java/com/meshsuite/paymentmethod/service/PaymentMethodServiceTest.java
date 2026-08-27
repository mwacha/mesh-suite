package com.meshsuite.paymentmethod.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import com.meshsuite.paymentmethod.dto.PaymentMethodInstallmentInput;
import com.meshsuite.paymentmethod.dto.PaymentMethodRequest;
import com.meshsuite.paymentmethod.exception.DuplicatePaymentMethodDescriptionException;
import com.meshsuite.paymentmethod.exception.PaymentMethodNotFoundException;
import com.meshsuite.paymentmethod.exception.PaymentMethodValidationException;
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
class PaymentMethodServiceTest extends AbstractIntegrationTest {

    @Autowired PaymentMethodService paymentMethodService;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PAYABLE, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private PaymentMethodRequest request(String description, Boolean active, List<PaymentMethodInstallmentInput> installments) {
        int max = installments == null ? 1 : Math.max(1, installments.size());
        return request(description, PaymentMethodType.BOLETO, active, max, installments);
    }

    private PaymentMethodRequest request(String description, PaymentMethodType type, Boolean active,
            Integer maxInstallments, List<PaymentMethodInstallmentInput> installments) {
        return new PaymentMethodRequest(description, type, null, active, maxInstallments, null, null, installments);
    }

    private List<PaymentMethodInstallmentInput> aVista() {
        return List.of(new PaymentMethodInstallmentInput(0, new BigDecimal("100.00")));
    }

    private List<PaymentMethodInstallmentInput> parcelado3x() {
        return List.of(
                new PaymentMethodInstallmentInput(30, new BigDecimal("34.00")),
                new PaymentMethodInstallmentInput(60, new BigDecimal("33.00")),
                new PaymentMethodInstallmentInput(90, new BigDecimal("33.00")));
    }

    @Test
    void createsAndRetrievesPaymentMethodWithInstallments() {
        setUpTenant("aurora-pm");

        var criado = paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        var buscado = paymentMethodService.findById(criado.id());
        assertThat(buscado.description()).isEqualTo("30/60/90");
        assertThat(buscado.active()).isTrue();
        assertThat(buscado.installments()).hasSize(3);
        assertThat(buscado.installments().get(0).installmentNumber()).isEqualTo(1);
        assertThat(buscado.installments().get(0).daysDue()).isEqualTo(30);
        assertThat(buscado.installments().get(2).daysDue()).isEqualTo(90);
    }

    @Test
    void createsAVistaPaymentMethod() {
        setUpTenant("aurora-pm");

        var criado = paymentMethodService.create(TenantContext.get(), request("À Vista", true, aVista()));

        assertThat(criado.installments()).hasSize(1);
        assertThat(criado.installments().get(0).daysDue()).isEqualTo(0);
        assertThat(criado.installments().get(0).percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsDuplicateDescriptionOnCreate() {
        setUpTenant("aurora-pm");
        paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        assertThatThrownBy(() -> paymentMethodService.create(TenantContext.get(), request("30/60/90", true, aVista())))
                .isInstanceOf(DuplicatePaymentMethodDescriptionException.class);
    }

    @Test
    void rejectsInstallmentsThatDoNotSumTo100Percent() {
        setUpTenant("aurora-pm");
        var invalido = List.of(
                new PaymentMethodInstallmentInput(30, new BigDecimal("40.00")),
                new PaymentMethodInstallmentInput(60, new BigDecimal("40.00")));

        assertThatThrownBy(() -> paymentMethodService.create(TenantContext.get(), request("Errado", true, invalido)))
                .isInstanceOf(PaymentMethodValidationException.class);
    }

    @Test
    void rejectsInstallmentListLongerThanTheDeclaredMaximum() {
        setUpTenant("aurora-pm");

        assertThatThrownBy(() -> paymentMethodService.create(TenantContext.get(),
                request("Curto demais", PaymentMethodType.DUPLICATA, true, 2, parcelado3x())))
                .isInstanceOf(PaymentMethodValidationException.class);
    }

    @Test
    void createsPaymentMethodWithoutInstallments() {
        setUpTenant("aurora-pm");

        var criado = paymentMethodService.create(TenantContext.get(),
                request("Cartão Crédito", PaymentMethodType.CARD, true, 12, null));

        assertThat(criado.installments()).isEmpty();
        assertThat(criado.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(criado.maxInstallments()).isEqualTo(12);
    }

    @Test
    void persistsTypeNotesAndConditionFields() {
        setUpTenant("aurora-pm");
        var request = new PaymentMethodRequest("Cartão Crédito", PaymentMethodType.CARD, "Bandeiras próprias", true,
                12, new BigDecimal("2.50"), 30, null);

        var criado = paymentMethodService.create(TenantContext.get(), request);
        var buscado = paymentMethodService.findById(criado.id());

        assertThat(buscado.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(buscado.notes()).isEqualTo("Bandeiras próprias");
        assertThat(buscado.maxInstallments()).isEqualTo(12);
        assertThat(buscado.interestRate()).isEqualByComparingTo("2.50");
        assertThat(buscado.settlementDays()).isEqualTo(30);
    }

    @Test
    void updateKeepsStoredInstallmentsWhenTheRequestOmitsThem() {
        setUpTenant("aurora-pm");
        var criado = paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        var atualizado = paymentMethodService.update(criado.id(),
                request("30/60/90", PaymentMethodType.DUPLICATA, true, 3, null));

        assertThat(atualizado.installments()).hasSize(3);
        assertThat(atualizado.type()).isEqualTo(PaymentMethodType.DUPLICATA);
    }

    @Test
    void updateReplacesTheWholeInstallmentList() {
        setUpTenant("aurora-pm");
        var criado = paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        var atualizado = paymentMethodService.update(criado.id(), request("30/60/90", true, aVista()));

        assertThat(atualizado.installments()).hasSize(1);
        assertThat(atualizado.installments().get(0).daysDue()).isEqualTo(0);
    }

    @Test
    void deletesPaymentMethodAndCascadesInstallments() {
        setUpTenant("aurora-pm");
        var criado = paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        paymentMethodService.delete(criado.id());

        assertThatThrownBy(() -> paymentMethodService.findById(criado.id()))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void listFiltersByActiveAndSearch() {
        setUpTenant("aurora-pm");
        paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));
        paymentMethodService.create(TenantContext.get(), request("À Vista", false, aVista()));

        var ativas = paymentMethodService.list(null, null, true, PageRequest.of(0, 10));
        assertThat(ativas.getContent()).extracting("description").containsExactly("30/60/90");

        var busca = paymentMethodService.list("vista", null, null, PageRequest.of(0, 10));
        assertThat(busca.getContent()).extracting("description").containsExactly("À Vista");
    }

    @Test
    void listFiltersByType() {
        setUpTenant("aurora-pm");
        paymentMethodService.create(TenantContext.get(), request("Pix", PaymentMethodType.PIX, true, 1, null));
        paymentMethodService.create(TenantContext.get(), request("Cartão Crédito", PaymentMethodType.CARD, true, 12, null));

        var cartoes = paymentMethodService.list(null, PaymentMethodType.CARD, null, PageRequest.of(0, 10));

        assertThat(cartoes.getContent()).extracting("description").containsExactly("Cartão Crédito");
    }

    @Test
    void summaryCarriesTypeMaxAndInstallmentDays() {
        setUpTenant("aurora-pm");
        paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));

        var resumo = paymentMethodService.list(null, null, null, PageRequest.of(0, 10)).getContent().get(0);

        assertThat(resumo.installmentDays()).containsExactly(30, 60, 90);
        assertThat(resumo.installmentsCount()).isEqualTo(3);
        assertThat(resumo.maxInstallments()).isEqualTo(3);
    }

    @Test
    void countsSplitsActiveAndInactive() {
        setUpTenant("aurora-pm");
        paymentMethodService.create(TenantContext.get(), request("30/60/90", true, parcelado3x()));
        paymentMethodService.create(TenantContext.get(), request("À Vista", false, aVista()));

        var counts = paymentMethodService.counts();

        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.active()).isEqualTo(1);
        assertThat(counts.inactive()).isEqualTo(1);
    }

    @Test
    void deniesCreateWhenCallerLacksPayableCreatePermission() {
        Tenant tenant = new Tenant();
        tenant.setCodigo("sem-permissao-pm");
        tenant.setNome("sem-permissao-pm");
        tenantRepository.saveAndFlush(tenant);
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();
        TenantContext.set(tenant.getId());

        User noPerms = new User();
        noPerms.setTenantId(tenant.getId());
        noPerms.setName("No Permissions");
        noPerms.setEmail("no-perms@sem-permissao-pm.com.br");
        noPerms.setPasswordHash("hash");
        noPerms.setRole(Role.SALES_REP);
        noPerms.setProfile(Profile.VIEWER);
        User saved = userRepository.saveAndFlush(noPerms);

        var principal = new AuthContextService.Context(saved.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> paymentMethodService.create(TenantContext.get(), request("30/60/90", true, aVista())))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
