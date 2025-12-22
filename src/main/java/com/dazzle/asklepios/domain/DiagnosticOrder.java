package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class DiagnosticOrder extends AbstractAuditingEntity implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "status", columnDefinition = "text")
    private String status;

    // DB-generated (sequence default). Must NOT be included in INSERT.
    @Generated(GenerationTime.INSERT)
    @Column(name = "order_number", nullable = false, updatable = false, insertable = false)
    private Long orderNumber;

    @Column(name = "save_draft")
    private Boolean saveDraft;

    @Column(name = "submitted_by", columnDefinition = "text")
    private String submittedBy;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent = false;

    @Column(name = "lab_status", columnDefinition = "text")
    private String labStatus;

    @Column(name = "rad_status", columnDefinition = "text")
    private String radStatus;
}
