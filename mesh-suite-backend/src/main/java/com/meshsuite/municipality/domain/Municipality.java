package com.meshsuite.municipality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global IBGE municipality reference data (see V19__create_municipality.sql) --
 * not tenant-scoped, no RLS, same rows for every tenant.
 */
@Entity
@Table(name = "municipality")
public class Municipality {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String state;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }
}
