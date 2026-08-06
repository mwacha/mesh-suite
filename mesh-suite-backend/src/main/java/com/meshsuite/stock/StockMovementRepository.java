package com.meshsuite.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
