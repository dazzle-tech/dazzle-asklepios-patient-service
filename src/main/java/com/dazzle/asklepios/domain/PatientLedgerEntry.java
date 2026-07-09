package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.LedgerAccount;
import com.dazzle.asklepios.domain.enumeration.LedgerEntryType;
import com.dazzle.asklepios.domain.enumeration.LedgerSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_ledger_entries")
public class PatientLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LedgerEntryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private LedgerSource source;

    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account", nullable = false)
    private LedgerAccount account;

    private BigDecimal amount;

    private String currency;

    private Instant createdDate;
}
