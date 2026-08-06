package com.meshsuite.stock;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UserRepository userRepository;
    @Autowired StockMovementRepository stockMovementRepository;
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

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextUnset() {
        Tenant tenant = createTenant("aurora");
        setTenantContext(tenant.getId());

        Produto produto = new Produto();
        produto.setTenantId(tenant.getId());
        produto.setNome("Tecido Algodão");
        produto.setSku("P0001");
        produto.setPrecoVenda(new BigDecimal("25.00"));
        produto = produtoRepository.saveAndFlush(produto);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Carlos");
        user.setEmail("carlos@aurora.com.br");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMINISTRATIVE);
        user = userRepository.saveAndFlush(user);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenant.getId());
        movement.setProduct(produto);
        movement.setType(StockMovementType.INBOUND);
        movement.setQuantity(new BigDecimal("5.000"));
        movement.setOrigin(StockMovementOrigin.MANUAL);
        movement.setBalanceAfter(new BigDecimal("5.000"));
        movement.setUser(user);
        stockMovementRepository.saveAndFlush(movement);
        entityManager.clear();

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        Long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM stock_movement")
                .getSingleResult()).longValue();
        assertThat(count).isZero();
    }
}
