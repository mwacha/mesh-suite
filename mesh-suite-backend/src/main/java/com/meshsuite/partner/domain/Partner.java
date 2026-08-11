package com.meshsuite.partner.domain;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "partner")
@Getter
@Setter
public class Partner {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 20)
    private PersonType personType;

    @Column(nullable = false, length = 14)
    private String document;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(name = "legal_name")
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartnerStatus status = PartnerStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "partner_role", joinColumns = @JoinColumn(name = "partner_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<PartnerRole> roles = new HashSet<>();

    @Column(name = "billing_emails", length = 500)
    private String billingEmails;

    @Column(length = 20)
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_indicator", length = 20)
    private TaxIndicator taxIndicator;

    @Column(name = "state_registration", length = 20)
    private String stateRegistration;

    @Column(name = "municipal_registration", length = 20)
    private String municipalRegistration;

    @Column(name = "suframa_registration", length = 20)
    private String suframaRegistration;

    @Column(length = 8)
    private String zipCode;

    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 100)
    private String complement;

    @Column(length = 2)
    private String state;

    @Column(length = 100)
    private String city;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "partner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartnerContact> contacts = new ArrayList<>();
}
