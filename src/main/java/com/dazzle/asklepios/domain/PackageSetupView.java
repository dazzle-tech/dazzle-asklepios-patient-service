package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** Read-only view of the setup-owned {@code package_setup} table. */
@Entity
@Immutable
@Table(name = "package_setup")
@Getter
@NoArgsConstructor
public class PackageSetupView {

    @Id
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "valid_from")
    private java.time.LocalDate validFrom;

    @Column(name = "valid_to")
    private java.time.LocalDate validTo;

    @Column(name = "all_departments")
    private Boolean allDepartments;
}
