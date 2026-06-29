package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.MergedStatus;
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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_merge_logs")
public class PatientMergeLog  implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_patient_id", nullable = false)
    private Patient fromPatient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_patient_id", nullable = false)
    private Patient toPatient;

    @Enumerated(EnumType.STRING)
    @Column(name = "merge_status", nullable = false, length = 50)
    private MergedStatus mergeStatus;

    @Column(name = "merged_by", nullable = false, length = 50)
    private String mergedBy;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt;


    @Column(name = "undo_by", length = 50)
    private String undoBy;

    @Column(name = "undo_at")
    private Instant undoAt;

    @NotNull
    @Column(name = "reason", length = 500)
    private String reason;
    @NotNull
    @Column(name = "transaction_number", unique = true, length = 30)
    private String transactionNumber;
}