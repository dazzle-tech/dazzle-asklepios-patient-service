package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_eligibility_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingEligibilitySnapshot
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false, unique = true)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "patient_insurance_id")
    private Long patientInsuranceId;

    @Column(name = "waseel_eligibility_request_id", nullable = false)
    private Long waseelEligibilityRequestId;

    @Column(name = "eligibility_response_id", nullable = false, length = 100)
    private String eligibilityResponseId;

    @Column(name = "member_id", length = 100)
    private String memberId;

    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    @Column(name = "policy_holder", length = 255)
    private String policyHolder;

    @Column(name = "network", length = 100)
    private String network;

    @Column(name = "coverage_status", length = 100)
    private String coverageStatus;

    @Column(name = "inforce", length = 50)
    private String inforce;

    @Column(name = "copayment_percent", precision = 19, scale = 6)
    private BigDecimal copaymentPercent;

    @Column(name = "copayment_cap", precision = 19, scale = 4)
    private BigDecimal copaymentCap;

    @Column(name = "coverage_json", columnDefinition = "text")
    private String coverageJson;

    @Column(name = "response_json", nullable = false, columnDefinition = "text")
    private String responseJson;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;

    @Column(name = "frozen_by", nullable = false, length = 50)
    private String frozenBy;
}
