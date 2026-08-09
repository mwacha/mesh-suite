package com.meshsuite.stock.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.produto.exception.ProdutoNaoEncontradoException;
import com.meshsuite.produto.repository.ProdutoRepository;
import com.meshsuite.stock.domain.StockMovement;
import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;
import com.meshsuite.stock.dto.StockMovementResponse;
import com.meshsuite.stock.exception.StockValidationException;
import com.meshsuite.stock.repository.StockMovementRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockMovementRepository stockMovementRepository;
    private final ProdutoRepository produtoRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public StockService(StockMovementRepository stockMovementRepository, ProdutoRepository produtoRepository,
                         UserRepository userRepository, EntityManager entityManager) {
        this.stockMovementRepository = stockMovementRepository;
        this.produtoRepository = produtoRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public StockMovementResponse adjustBalance(UUID tenantId, UUID productId, StockMovementType type,
                                                BigDecimal quantity, StockMovementOrigin origin,
                                                UUID referenceId, UUID userId, String note) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockValidationException("A quantidade deve ser maior que zero");
        }

        Produto product = produtoRepository.findById(productId)
                .orElseThrow(ProdutoNaoEncontradoException::new);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new StockValidationException("Usuário responsável não encontrado"));

        BigDecimal newBalance = applyAtomicAdjustment(productId, type, quantity);
        // The native UPDATE above bypasses Hibernate, so the `product` entity already
        // loaded into this transaction's persistence context (line above) is now stale
        // in memory even though the DB row is correct. Refresh it so any same-transaction
        // reader (e.g. a caller re-reading Produto right after this call) doesn't see the
        // pre-adjustment value from the first-level cache instead of the committed one.
        entityManager.refresh(product);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setOrigin(origin);
        movement.setReferenceId(referenceId);
        movement.setBalanceAfter(newBalance);
        movement.setUser(user);
        movement.setNote(note);

        return toResponse(stockMovementRepository.saveAndFlush(movement));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.STOCK, action = Action.VIEW)
    public Page<StockMovementResponse> history(UUID productId, Pageable pageable) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable).map(this::toResponse);
    }

    // Atomic UPDATE ... RETURNING against the product's own balance column --
    // never read-then-write. For OUTBOUND, the WHERE clause itself guards
    // against a negative result (quantidade_estoque >= :quantity): if the
    // guard fails, zero rows match and the query returns no result, all
    // inside the same atomic statement -- a separate check-then-update would
    // reopen the exact race condition this pattern exists to avoid.
    private BigDecimal applyAtomicAdjustment(UUID productId, StockMovementType type, BigDecimal quantity) {
        String sql = type == StockMovementType.INBOUND
                ? "UPDATE produto SET quantidade_estoque = quantidade_estoque + :quantity " +
                        "WHERE id = :productId RETURNING quantidade_estoque"
                : "UPDATE produto SET quantidade_estoque = quantidade_estoque - :quantity " +
                        "WHERE id = :productId AND quantidade_estoque >= :quantity RETURNING quantidade_estoque";

        List<?> result = entityManager.createNativeQuery(sql)
                .setParameter("quantity", quantity)
                .setParameter("productId", productId)
                .getResultList();

        if (result.isEmpty()) {
            throw new StockValidationException("Saldo insuficiente para esta saída");
        }
        return (BigDecimal) result.get(0);
    }

    private StockMovementResponse toResponse(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getNome(), m.getType(),
                m.getQuantity(), m.getOrigin(), m.getReferenceId(), m.getBalanceAfter(),
                m.getUser().getId(), m.getUser().getName(), m.getNote(), m.getCreatedAt());
    }
}
