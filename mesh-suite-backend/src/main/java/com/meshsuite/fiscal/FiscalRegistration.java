package com.meshsuite.fiscal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fiscal_registration")
@Getter
@Setter
public class FiscalRegistration {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String description;

    @Column(length = 10)
    private String cfop;

    @Column(name = "icms_cst", length = 10)
    private String icmsCst;

    @Column(name = "icms_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal icmsRate;

    @Column(name = "ipi_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal ipiRate;

    @Column(name = "pis_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal pisRate;

    @Column(name = "cofins_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cofinsRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
