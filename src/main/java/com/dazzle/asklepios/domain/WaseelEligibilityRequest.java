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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "waseel_eligibility_request")
public class WaseelEligibilityRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "patient_insurance_id", nullable = false)
    private Long patientInsuranceId;

    @Column(name = "payor_id")
    private Long payorId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "destination_id", length = 100)
    private String destinationId;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "benefits")
    private Boolean benefits;

    @Column(name = "discovery")
    private Boolean discovery;

    @Column(name = "validation")
    private Boolean validation;

    @Column(name = "transfer")
    private Boolean transfer;

    @Column(name = "emergency")
    private Boolean emergency;

    @Column(name = "api_status", length = 50)
    private String apiStatus;

    @Column(name = "status_code", length = 50)
    private String statusCode;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "eligibility_response_id", length = 100)
    private String eligibilityResponseId;

    @Column(name = "eligibility_response_url", length = 500)
    private String eligibilityResponseUrl;

    @Column(name = "waseel_request_id", length = 100)
    private String waseelRequestId;

    @Column(name = "request_status", length = 50)
    private String requestStatus;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;
}
