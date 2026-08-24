package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "tipo_variacao_valor")
@Getter
@Setter
public class TipoVariacaoValor {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_variacao_id", nullable = false)
    private TipoVariacao tipoVariacao;

    @Column(nullable = false, length = 100)
    private String valor;

    @Column(nullable = false)
    private Integer ordem;
}
