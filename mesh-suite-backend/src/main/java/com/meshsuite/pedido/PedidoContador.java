package com.meshsuite.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pedido_contador")
@Getter
@Setter
public class PedidoContador {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "proximo_numero", nullable = false)
    private Integer proximoNumero = 1;
}
