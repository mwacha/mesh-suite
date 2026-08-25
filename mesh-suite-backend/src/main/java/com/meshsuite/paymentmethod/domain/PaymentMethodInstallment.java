package com.meshsuite.paymentmethod.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payment_method_installment")
@Getter
@Setter
public class PaymentMethodInstallment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "days_due", nullable = false)
    private Integer daysDue;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;
}
