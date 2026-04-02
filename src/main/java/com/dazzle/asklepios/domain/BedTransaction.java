package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "bed_transactions")
public class BedTransaction extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "from_room_id")
    private Long fromRoomId;

    @Column(name = "from_bed_id")
    private Long fromBedId;

    @Column(name = "to_room_id")
    private Long toRoomId;

    @Column(name = "to_bed_id")
    private Long toBedId;

    @NotNull
    @Column(name = "from_department_id", nullable = false)
    private Long fromDepartmentId;

    @NotNull
    @Column(name = "to_department_id", nullable = false)
    private Long toDepartmentId;

    @NotNull
    @Column(name = "is_external", nullable = false)
    private Boolean isExternal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private BedTransactionType transactionType;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    @Builder.Default
    private Instant transactionDate = Instant.now();
}