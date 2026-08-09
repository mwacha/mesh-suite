package com.meshsuite.parceiro.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "parceiro_contato")
@Getter
@Setter
public class ParceiroContato {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parceiro_id", nullable = false)
    private Parceiro parceiro;

    @Column(nullable = false)
    private String nome;

    private String email;

    @Column(name = "telefone_comercial", length = 20)
    private String telefoneComercial;

    @Column(name = "telefone_celular", length = 20)
    private String telefoneCelular;

    @Column(length = 100)
    private String cargo;
}
