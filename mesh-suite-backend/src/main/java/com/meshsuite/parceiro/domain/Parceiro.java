package com.meshsuite.parceiro.domain;

import com.meshsuite.parceiro.domain.enums.IndicadorIe;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
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
@Table(name = "parceiro")
@Getter
@Setter
public class Parceiro {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 10)
    private TipoPessoa tipoPessoa;

    @Column(nullable = false, length = 14)
    private String documento;

    @Column(name = "nome_fantasia", nullable = false)
    private String nomeFantasia;

    @Column(name = "razao_social")
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusParceiro status = StatusParceiro.ATIVO;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parceiro_papel", joinColumns = @JoinColumn(name = "parceiro_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private Set<PapelParceiro> papeis = new HashSet<>();

    @Column(name = "emails_cobranca", length = 500)
    private String emailsCobranca;

    @Column(length = 20)
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicador_ie", length = 20)
    private IndicadorIe indicadorIe;

    @Column(name = "inscricao_estadual", length = 20)
    private String inscricaoEstadual;

    @Column(name = "inscricao_municipal", length = 20)
    private String inscricaoMunicipal;

    @Column(name = "inscricao_suframa", length = 20)
    private String inscricaoSuframa;

    @Column(length = 8)
    private String cep;

    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String complemento;

    @Column(length = 2)
    private String uf;

    @Column(length = 100)
    private String cidade;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "parceiro", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ParceiroContato> contatos = new ArrayList<>();
}
