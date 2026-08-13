package com.meshsuite.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.stock.domain.StockMovement;
import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;
import com.meshsuite.stock.repository.StockMovementRepository;
import com.meshsuite.tenant.domain.Tenant;
import com.meshsuite.tenant.repository.TenantRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class StockMovementRepositoryTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired ProductRepository produtoRepository;
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

        Product produto = new Product();
        produto.setTenantId(tenant.getId());
        produto.setName("Tecido Algodão");
        produto.setSku("P0001");
        produto.setSalePrice(new BigDecimal("25.00"));
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
