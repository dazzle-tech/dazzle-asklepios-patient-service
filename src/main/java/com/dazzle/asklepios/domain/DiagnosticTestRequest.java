package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_test_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticTestRequest extends AbstractAuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DiagnosticTestRequestStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TestType type;

    @NotNull
    @Column(name = "name")
    private String name;

    @NotNull
    @Column(name = "indication", columnDefinition = "text")
    private String indication;
    @Column(name = "diagnostic_test_id")
    private Long diagnosticTestId;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "approved_date")
    private Instant approvedDate;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejected_reason", length = 200)
    private String rejectedReason;

    @NotNull
    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @NotNull
    @Column(name = "from_facility_id")
    private Long fromFacilityId;

}
