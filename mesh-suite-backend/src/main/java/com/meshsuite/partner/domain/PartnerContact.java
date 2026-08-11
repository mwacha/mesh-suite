package com.meshsuite.partner.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "partner_contact")
@Getter
@Setter
public class PartnerContact {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(nullable = false)
    private String name;

    private String email;

    @Column(name = "business_phone", length = 20)
    private String businessPhone;

    @Column(name = "mobile_phone", length = 20)
    private String mobilePhone;

    @Column(length = 100)
    private String jobTitle;
}
