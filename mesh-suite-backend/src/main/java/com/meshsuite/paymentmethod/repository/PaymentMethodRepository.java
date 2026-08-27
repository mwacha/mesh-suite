package com.meshsuite.paymentmethod.repository;

import com.meshsuite.paymentmethod.domain.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentMethodRepository
        extends JpaRepository<PaymentMethod, UUID>, JpaSpecificationExecutor<PaymentMethod> {
    boolean existsByDescription(String description);
    boolean existsByDescriptionAndIdNot(String description, UUID id);
    long countByActive(boolean active);
}
