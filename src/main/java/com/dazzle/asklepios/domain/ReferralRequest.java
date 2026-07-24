package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ReferralPriority;
import com.dazzle.asklepios.domain.enumeration.ReferralStatus;
import com.dazzle.asklepios.domain.enumeration.ReferralType;
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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "referral_requests")
public class ReferralRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "referral_type", nullable = false, length = 20)
    @Builder.Default
    private ReferralType referralType = ReferralType.INTERNAL;

    @NotNull
    @Column(name = "from_facility_id", nullable = false)
    private Long fromFacilityId;

    @NotNull
    @Column(name = "to_facility_id", nullable = false)
    private Long toFacilityId;

    @NotNull
    @Column(name = "from_department_id", nullable = false)
    private Long fromDepartmentId;

    @NotNull
    @Column(name = "to_department_id", nullable = false)
    private Long toDepartmentId;

    @NotBlank
    @Column(name = "referral_reason", nullable = false, length = 1000)
    private String referralReason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private ReferralPriority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.REQUESTED;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Column(name = "accepted_date")
    private Instant acceptedDate;

    @Column(name = "accepted_by", length = 50)
    private String acceptedBy;

    @AssertTrue(message = "Reject reason is required when status is REJECTED")
    public boolean isRejectReasonValid() {
        return status != ReferralStatus.REJECTED
                || (rejectReason != null && !rejectReason.isBlank());
    }

    @AssertTrue(message = "Accepted fields are required when status is ACCEPTED")
    public boolean isAcceptedFieldsValid() {
        return status != ReferralStatus.ACCEPTED
                || (acceptedDate != null && acceptedBy != null && !acceptedBy.isBlank());
    }
}
