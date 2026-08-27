package com.meshsuite.pricetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.dto.PriceTableItemInput;
import com.meshsuite.pricetable.dto.PriceTableRequest;
import com.meshsuite.pricetable.exception.PriceTableNotFoundException;
import com.meshsuite.pricetable.exception.DuplicatePriceTableNameException;
import com.meshsuite.pricetable.exception.PriceTableValidationException;
import com.meshsuite.pricetable.service.PriceTableService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.shared.context.TenantContext;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.UserPermissionGrant;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

class PriceTableServiceTest extends AbstractIntegrationTest {

    @Autowired PriceTableService tabelaPrecoService;
    @Autowired ProductRepository produtoRepository;
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
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.VIEW));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.CREATE));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.EDIT));
        caller.getPermissions().add(new UserPermissionGrant(Module.PRODUCT, Action.DELETE));
        User savedCaller = userRepository.saveAndFlush(caller);

        var principal = new AuthContextService.Context(savedCaller.getId(), tenant.getId(), "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return tenant.getId();
    }

    private Product novoProduto(UUID tenantId, String sku, BigDecimal precoVenda) {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Produto " + sku);
        p.setSku(sku);
        p.setSalePrice(precoVenda);
        return produtoRepository.saveAndFlush(p);
    }

    private PriceTableRequest request(String nome, List<PriceTableItemInput> itens) {
        return new PriceTableRequest(nome, ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, null, itens);
    }

    @Test
    @Transactional
    void createsAndRetrievesPriceTableWithItems() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("59.90"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("69.90"), new BigDecimal("5.00")))));

        var buscada = tabelaPrecoService.buscarPorId(criada.id());
        assertThat(buscada.name()).isEqualTo("Varejo");
        assertThat(buscada.items()).hasSize(1);
        assertThat(buscada.items().get(0).productId()).isEqualTo(produto.getId());
        assertThat(buscada.items().get(0).tablePrice()).isEqualByComparingTo("69.90");
        assertThat(buscada.items().get(0).registeredPrice()).isEqualByComparingTo("59.90");
    }

    @Test
    @Transactional
    void doesNotRecalculatePricesServerSide() {
        // Global Constraints: the backend persists exactly what the client sends,
        // even a price wildly different from produto.salePrice -- there is no
        // server-side formula to disagree with the client.
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Promo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("999.99"), null))));

        assertThat(criada.items().get(0).tablePrice()).isEqualByComparingTo("999.99");
    }

    @Test
    @Transactional
    void rejectsDuplicateNameOnCreate() {
        setUpTenant("aurora-tp");
        tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of()));

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of())))
                .isInstanceOf(DuplicatePriceTableNameException.class);
    }

    @Test
    @Transactional
    void updateReplacesTheWholeItemList() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produtoA = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        Product produtoB = novoProduto(tenantId, "P0002", new BigDecimal("20.00"));

        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produtoA.getId(), new BigDecimal("15.00"), null))));

        var atualizada = tabelaPrecoService.atualizar(criada.id(),
                request("Varejo", List.of(new PriceTableItemInput(produtoB.getId(), new BigDecimal("25.00"), null))));

        assertThat(atualizada.items()).hasSize(1);
        assertThat(atualizada.items().get(0).productId()).isEqualTo(produtoB.getId());
    }

    @Test
    @Transactional
    void rejectsItemWithUnknownProduct() {
        setUpTenant("aurora-tp");
        UUID produtoInexistente = UUID.randomUUID();

        assertThatThrownBy(() -> tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produtoInexistente, new BigDecimal("10.00"), null)))))
                .isInstanceOf(PriceTableValidationException.class);
    }

    @Test
    @Transactional
    void deletesPriceTableAndCascadesItems() {
        UUID tenantId = setUpTenant("aurora-tp");
        Product produto = novoProduto(tenantId, "P0001", new BigDecimal("10.00"));
        var criada = tabelaPrecoService.criar(TenantContext.get(),
                request("Varejo", List.of(new PriceTableItemInput(produto.getId(), new BigDecimal("15.00"), null))));

        tabelaPrecoService.excluir(criada.id());

        assertThatThrownBy(() -> tabelaPrecoService.buscarPorId(criada.id()))
                .isInstanceOf(PriceTableNotFoundException.class);
    }

    @Test
    @Transactional
    void statusUpdateTogglesActiveAndCounts() {
        setUpTenant("aurora-tp");
        var criada = tabelaPrecoService.criar(TenantContext.get(), request("Varejo", List.of()));
        assertThat(criada.active()).isTrue();

        var contagensAntes = tabelaPrecoService.counts();
        assertThat(contagensAntes.active()).isEqualTo(1);
        assertThat(contagensAntes.inactive()).isEqualTo(0);

        var inativada = tabelaPrecoService.atualizarStatus(criada.id(), false);
        assertThat(inativada.active()).isFalse();

        var contagensDepois = tabelaPrecoService.counts();
        assertThat(contagensDepois.total()).isEqualTo(1);
        assertThat(contagensDepois.active()).isEqualTo(0);
        assertThat(contagensDepois.inactive()).isEqualTo(1);
    }

    @Test
    @Transactional
    void listFiltersByActive() {
        setUpTenant("aurora-tp");
        var requestAtiva = new PriceTableRequest("Ativa", ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, true, List.of());
        var requestInativa = new PriceTableRequest("Inativa", ProductSelectionMode.SELECT_PRODUCTS, AdjustmentMethod.MANUAL,
                null, null, null, Rounding.NO_ROUNDING, LocalDate.of(2026, 1, 1), null, null, null, false, List.of());
        tabelaPrecoService.criar(TenantContext.get(), requestAtiva);
        tabelaPrecoService.criar(TenantContext.get(), requestInativa);

        var ativas = tabelaPrecoService.listar(null, true, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(ativas.getContent()).extracting("name").containsExactly("Ativa");
    }
}
