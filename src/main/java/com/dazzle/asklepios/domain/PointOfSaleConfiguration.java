package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "point_of_sale_configuration")
@Getter
@Setter
public class PointOfSaleConfiguration extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @NotNull
    @Column(name = "terminal_id", nullable = false, unique = true)
    private String terminalId;

    @Column(name = "terminal_serial_no")
    private String terminalSerialNo;

    @Column(name = "terminal_type")
    private String terminalType;

    @Column(name = "counter_number")
    private String counterNumber;

    @Column(name = "cash_register_no")
    private String cashRegisterNo;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    // getters setters equals hashCode
}
