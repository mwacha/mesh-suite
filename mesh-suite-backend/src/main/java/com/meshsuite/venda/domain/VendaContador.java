package com.meshsuite.venda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venda_contador")
@Getter
@Setter
public class VendaContador {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "proximo_numero", nullable = false)
    private Integer proximoNumero = 1;
}
