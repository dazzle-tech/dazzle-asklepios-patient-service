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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "hospitalizations")
@EqualsAndHashCode(callSuper = false)
public class Hospitalization extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn( name = "patient_id", nullable = false)
    private Patient patient;

    @NotBlank
    @Column(name = "facility", nullable = false)
    private String facility;

    @NotBlank
    @Column(name = "reason", nullable = false)
    private String reason;

    @NotNull
    @Column(name = "admission_type", nullable = false, length = 100)
    private String admissionType;

    @NotNull
    @Column(name = "date_of_admission", nullable = false)
    private Date dateOfAdmission;

    @Column(name = "length_of_stay_days")
    private Integer lengthOfStayDays;

    @Column(name = "outcomes")
    private String outcomes;

    @Column(name = "medical_interventions_performed", length = 1000)
    private String medicalInterventionsPerformed;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Date cancelledDate;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

}
