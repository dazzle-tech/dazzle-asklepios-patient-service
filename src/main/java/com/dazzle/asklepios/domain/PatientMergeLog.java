package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    @Column(name = "merge_status", nullable = false, length = 50)
    private String mergeStatus;

    @Column(name = "merged_by", nullable = false, length = 50)
    private String mergedBy;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt;


    @Column(name = "undone_by", length = 50)
    private String undoneBy;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @Column(name = "reason", length = 500)
    private String reason;
}