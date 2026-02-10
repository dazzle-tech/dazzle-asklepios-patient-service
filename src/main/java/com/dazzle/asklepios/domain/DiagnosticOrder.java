
package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DiagnosticOrder extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id")
    private Long patientId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "encounter_id")
    private Long encounterId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DiagnosticStatus status=DiagnosticStatus.NEW;;

    @Generated(GenerationTime.INSERT)
    @Column(name = "order_number", nullable = false)
    private Long orderNumber;

    @NotNull
    @Column(name = "save_draft")
    private Boolean saveDraft = true;

    @Column(name = "submitted_by", length = 50)
    private String submittedBy;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @NotNull
    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "lab_status", length = 50)
    private DiagnosticStatus labStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "rad_status", length = 50)
    private DiagnosticStatus radStatus;

    @NotNull
    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @NotNull
    @Column(name = "from_facility_id")
    private Long fromFacilityId;
}
