package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "patient_wallet")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientWallet implements Serializable {

    @Id
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "last_modified_date", nullable = false)
    private Instant lastModifiedDate;
}