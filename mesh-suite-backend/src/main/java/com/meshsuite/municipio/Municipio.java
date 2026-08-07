package com.meshsuite.municipio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global IBGE municipality reference data (see V19__create_municipio.sql) --
 * not tenant-scoped, no RLS, same rows for every tenant.
 */
@Entity
@Table(name = "municipio")
public class Municipio {

    @Id
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 2)
    private String uf;

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getUf() {
        return uf;
    }
}
