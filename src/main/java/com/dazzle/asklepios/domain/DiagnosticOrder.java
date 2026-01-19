
package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
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

    @Column(name = "encounter_id")
    private Long encounterId;

    // Liquibase: varchar(50)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DiagnosticStatus status;

    // DB-generated (sequence default). Must NOT be included in INSERT.
    @Generated(GenerationTime.INSERT)
    @Column(name = "order_number", nullable = false, updatable = false, insertable = false)
    private Long orderNumber;

    @Column(name = "save_draft")
    private Boolean saveDraft = true;

    // Liquibase: varchar(50)
    @Column(name = "submitted_by", length = 50)
    private String submittedBy;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent = false;

    // Liquibase: varchar(50)
    @Enumerated(EnumType.STRING)
    @Column(name = "lab_status", length = 50)
    private DiagnosticStatus labStatus;

    // Liquibase: varchar(50)
    @Enumerated(EnumType.STRING)
    @Column(name = "rad_status", length = 50)
    private DiagnosticStatus radStatus;

    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @Column(name = "from_facility_id")
    private Long fromFacilityId;
}
