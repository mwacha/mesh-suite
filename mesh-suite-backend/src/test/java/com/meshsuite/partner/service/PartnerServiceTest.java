package com.meshsuite.partner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.dto.PartnerContactDto;
import com.meshsuite.partner.dto.PartnerRequest;
import com.meshsuite.partner.exception.DuplicateDocumentException;
import com.meshsuite.partner.exception.PartnerNotFoundException;
import com.meshsuite.partner.exception.PartnerValidationException;
import com.meshsuite.partner.service.PartnerService;
import com.meshsuite.paymentmethod.domain.PaymentMethod;
import com.meshsuite.paymentmethod.domain.PaymentMethodInstallment;
import com.meshsuite.paymentmethod.repository.PaymentMethodRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Profile;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PartnerServiceTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired PartnerService partnerService;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;
    @Autowired PaymentMethodRepository paymentMethodRepository;

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
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.CUSTOMER, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private PartnerRequest request(String documento, Set<PartnerRole> papeis) {
        return request(documento, papeis, null);
    }

    private PartnerRequest request(String documento, Set<PartnerRole> papeis, UUID paymentMethodId) {
        return new PartnerRequest(
                PersonType.LEGAL_ENTITY, documento, "Mercado Silva", "Mercado Silva Ltda", papeis,
                "financeiro@mercadosilva.com.br", "(11) 99999-9999", TaxIndicator.TAXPAYER,
                "123456789", null, null,
                "01310100", "Av. Paulista", "1000", "Bela Vista", null, "SP", "São Paulo",
                "Cliente antigo", List.of(new PartnerContactDto("Ana Souza", "ana@mercadosilva.com.br",
                        "(11) 3333-3333", "(11) 98888-8888", "Financeiro")),
                paymentMethodId);
    }

    private PaymentMethod criarFormaPagamento(UUID tenantId, String description) {
        PaymentMethod pm = new PaymentMethod();
        pm.setTenantId(tenantId);
        pm.setDescription(description);
        PaymentMethodInstallment installment = new PaymentMethodInstallment();
        installment.setPaymentMethod(pm);
        installment.setInstallmentNumber(1);
        installment.setDaysDue(0);
        installment.setPercentage(new java.math.BigDecimal("100.00"));
        pm.getInstallments().add(installment);
        return paymentMethodRepository.saveAndFlush(pm);
    }

    @Test
    void createsAndRetrievesPartner() {
        setUpTenant("aurora");

        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        var buscado = partnerService.findById(criado.id());
        assertThat(buscado.tradeName()).isEqualTo("Mercado Silva");
        assertThat(buscado.roles()).containsExactly(PartnerRole.CUSTOMER);
        assertThat(buscado.contacts()).hasSize(1);
    }

    @Test
    void acceptsCnpjWithMaskAndStoresOnlyDigits() {
        setUpTenant("aurora");

        var criado = partnerService.create(TenantContext.get(),
                request("00.062.452/0001-06", Set.of(PartnerRole.SUPPLIER)));

        assertThat(criado.document()).isEqualTo("00062452000106");
    }

    @Test
    void rejectsPartnerWithoutCustomerOrSupplierRole() {
        setUpTenant("aurora");

        assertThrows(PartnerValidationException.class,
                () -> partnerService.create(TenantContext.get(),
                        request("11222333000144", Set.of(PartnerRole.CARRIER))));
    }

    @Test
    void rejectsDuplicateDocumentInSameTenant() {
        setUpTenant("aurora");
        partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        assertThrows(DuplicateDocumentException.class,
                () -> partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.SUPPLIER))));
    }

    @Test
    void rejectsStatusUpdateToAtRisk() {
        setUpTenant("aurora");
        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        assertThrows(PartnerValidationException.class,
                () -> partnerService.updateStatus(criado.id(), PartnerStatus.AT_RISK));
    }

    @Test
    void updatesStatusToBlocked() {
        setUpTenant("aurora");
        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        var atualizado = partnerService.updateStatus(criado.id(), PartnerStatus.BLOCKED);

        assertThat(atualizado.status()).isEqualTo(PartnerStatus.BLOCKED);
    }

    @Test
    void summaryCountsByStatus() {
        setUpTenant("aurora");
        var a = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.SUPPLIER)));
        partnerService.updateStatus(a.id(), PartnerStatus.BLOCKED);

        var resumo = partnerService.summary(null);

        assertThat(resumo.total()).isEqualTo(2);
        assertThat(resumo.active()).isEqualTo(1);
        assertThat(resumo.blocked()).isEqualTo(1);
    }

    @Test
    void summaryCountsOnlyTheGivenRole() {
        setUpTenant("aurora");
        partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        var clienteBloqueado = partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.CUSTOMER)));
        partnerService.create(TenantContext.get(), request("00062452000106", Set.of(PartnerRole.SUPPLIER)));
        partnerService.updateStatus(clienteBloqueado.id(), PartnerStatus.BLOCKED);

        var resumoClientes = partnerService.summary(PartnerRole.CUSTOMER);

        assertThat(resumoClientes.total()).isEqualTo(2);
        assertThat(resumoClientes.active()).isEqualTo(1);
        assertThat(resumoClientes.blocked()).isEqualTo(1);
    }

    @Test
    void listsWithSearchFilter() {
        setUpTenant("aurora");
        partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        var pagina = partnerService.list("silva", null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).tradeName()).isEqualTo("Mercado Silva");
    }

    @Test
    void listsWithPartialDocumentFilterIgnoringMask() {
        setUpTenant("aurora");
        var alvo = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.CUSTOMER)));

        var comMascara = partnerService.list(null, null, null, "112.223.330/0014-4", null, null, null, PageRequest.of(0, 10));
        assertThat(comMascara.getTotalElements()).isEqualTo(1);
        assertThat(comMascara.getContent().get(0).id()).isEqualTo(alvo.id());

        var parcial = partnerService.list(null, null, null, "22333", null, null, null, PageRequest.of(0, 10));
        assertThat(parcial.getTotalElements()).isEqualTo(1);
        assertThat(parcial.getContent().get(0).id()).isEqualTo(alvo.id());

        var semCorrespondencia = partnerService.list(null, null, null, "99999", null, null, null, PageRequest.of(0, 10));
        assertThat(semCorrespondencia.getTotalElements()).isEqualTo(0);
    }

    @Test
    void listsWithRoleFilter() {
        setUpTenant("aurora");
        partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.SUPPLIER)));

        var pagina = partnerService.list(null, null, null, null, null, null, PartnerRole.CUSTOMER, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsWithMultiValueStatusFilter() {
        setUpTenant("aurora");
        var ativo1 = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        var ativo2 = partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.CUSTOMER)));
        var bloqueado = partnerService.create(TenantContext.get(), request("00062452000106", Set.of(PartnerRole.CUSTOMER)));
        partnerService.updateStatus(bloqueado.id(), PartnerStatus.BLOCKED);

        var apenasBloqueado = partnerService.list(null, List.of(PartnerStatus.BLOCKED), null, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(apenasBloqueado.getTotalElements()).isEqualTo(1);
        assertThat(apenasBloqueado.getContent().get(0).id()).isEqualTo(bloqueado.id());

        var ativoEBloqueado = partnerService.list(null, List.of(PartnerStatus.ACTIVE, PartnerStatus.BLOCKED), null, null, null,
                null, null, PageRequest.of(0, 10));
        assertThat(ativoEBloqueado.getTotalElements()).isEqualTo(3);
        assertThat(ativoEBloqueado.getContent()).extracting("id")
                .containsExactlyInAnyOrder(ativo1.id(), ativo2.id(), bloqueado.id());
    }

    @Test
    void listsWithMultiValueStateFilter() {
        setUpTenant("aurora");
        var sp = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        var outraUf = new PartnerRequest(
                PersonType.LEGAL_ENTITY, "55666777000155", "Comércio Rio", "Comércio Rio Ltda", Set.of(PartnerRole.CUSTOMER),
                "financeiro@comerciorio.com.br", "(21) 99999-9999", TaxIndicator.TAXPAYER, "987654321", null, null,
                "20000000", "Av. Rio Branco", "1", "Centro", null, "RJ", "Rio de Janeiro",
                null, List.of(), null);
        var rj = partnerService.create(TenantContext.get(), outraUf);

        var pagina = partnerService.list(null, null, null, null, List.of("SP", "RJ"), null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting("id").containsExactlyInAnyOrder(sp.id(), rj.id());
    }

    @Test
    void deletesPartner() {
        setUpTenant("aurora");
        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        partnerService.delete(criado.id());

        assertThrows(PartnerNotFoundException.class, () -> partnerService.findById(criado.id()));
    }

    @Test
    void updatesPartnerSuccessfully() {
        setUpTenant("aurora");
        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        var requestAtualizado = new PartnerRequest(
                PersonType.LEGAL_ENTITY, "11222333000144", "Mercado Silva Atualizado", "Mercado Silva Ltda",
                Set.of(PartnerRole.CUSTOMER), "financeiro@mercadosilva.com.br", "(11) 99999-9999",
                TaxIndicator.TAXPAYER, "123456789", null, null,
                "01310100", "Av. Paulista", "1000", "Bela Vista", null, "SP", "São Paulo",
                "Cliente antigo", List.of(new PartnerContactDto("Ana Souza", "ana@mercadosilva.com.br",
                        "(11) 3333-3333", "(11) 98888-8888", "Financeiro")), null);

        partnerService.update(criado.id(), requestAtualizado);

        var buscado = partnerService.findById(criado.id());
        assertThat(buscado.tradeName()).isEqualTo("Mercado Silva Atualizado");
    }

    @Test
    void updatesPartnerKeepingItsOwnDocument() {
        setUpTenant("aurora");
        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        var atualizado = partnerService.update(criado.id(),
                request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        assertThat(atualizado.document()).isEqualTo("11222333000144");
    }

    @Test
    void rejectsUpdateToAnotherPartnersDocument() {
        setUpTenant("aurora");
        partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));
        var outro = partnerService.create(TenantContext.get(), request("55666777000155", Set.of(PartnerRole.SUPPLIER)));

        assertThrows(DuplicateDocumentException.class,
                () -> partnerService.update(outro.id(), request("11222333000144", Set.of(PartnerRole.SUPPLIER))));
    }

    @Test
    void deniesListingWhenCallerLacksCustomerViewPermission() {
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

        assertThrows(com.meshsuite.auth.exception.PermissionDeniedException.class,
                () -> partnerService.list(null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    @Test
    void linksPaymentMethodToPartner() {
        UUID tenantId = setUpTenant("aurora");
        PaymentMethod formaPagamento = criarFormaPagamento(tenantId, "À Vista");

        var criado = partnerService.create(TenantContext.get(),
                request("11222333000144", Set.of(PartnerRole.CUSTOMER), formaPagamento.getId()));

        assertThat(criado.paymentMethodId()).isEqualTo(formaPagamento.getId());
        assertThat(criado.paymentMethodDescription()).isEqualTo("À Vista");
    }

    @Test
    void createsPartnerWithoutPaymentMethod() {
        setUpTenant("aurora");

        var criado = partnerService.create(TenantContext.get(), request("11222333000144", Set.of(PartnerRole.CUSTOMER)));

        assertThat(criado.paymentMethodId()).isNull();
        assertThat(criado.paymentMethodDescription()).isNull();
    }
}
