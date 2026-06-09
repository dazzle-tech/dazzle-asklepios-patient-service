package com.dazzle.asklepios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;


import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pre_authorization_request")
public class PreAuthorizationRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_insurance_id", nullable = false)
    private Long patientInsuranceId;

    @Column(name = "payor_id")
    private Long payorId;

    @Column(name = "payor_plan_id")
    private Long payorPlanId;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "provider_nphies_id", length = 100)
    private String providerNphiesId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "outgoing_transaction_id", length = 100)
    private String outgoingTransactionId;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "approval_response_id")
    private Long approvalResponseId;

    @Column(name = "pre_auth_ref_no", length = 100)
    private String preAuthRefNo;

    @Column(name = "eligibility_response_id", length = 100)
    private String eligibilityResponseId;

    @Column(name = "eligibility_response_url", length = 500)
    private String eligibilityResponseUrl;

    @Column(name = "eligibility_offline_id", length = 100)
    private String eligibilityOfflineId;

    @Column(name = "eligibility_offline_date")
    private LocalDate eligibilityOfflineDate;

    @Column(name = "date_ordered", nullable = false)
    private LocalDate dateOrdered;

    @Column(name = "payee_id", nullable = false)
    private Long payeeId;

    @Column(name = "payee_type", nullable = false, length = 50)
    private String payeeType = "provider";

    @Column(name = "preauth_type", nullable = false, length = 50)
    private String preauthType = "professional";

    @Column(name = "preauth_sub_type", nullable = false, length = 50)
    private String preauthSubType = "op";

    @Column(name = "episode_id", length = 100)
    private String episodeId;

    @Column(name = "prescription", length = 500)
    private String prescription;

    @Column(name = "transfer", nullable = false)
    private Boolean transfer = false;

    @Column(name = "is_new_born", nullable = false)
    private Boolean isNewBorn = false;

    @Column(name = "destination_id", length = 100)
    private String destinationId;

    @Column(name = "encounter_status", length = 50)
    private String encounterStatus;

    @Column(name = "encounter_class", length = 50)
    private String encounterClass;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    @Column(name = "service_event_type", length = 50)
    private String serviceEventType;

    @Column(name = "service_provider")
    private Long serviceProvider;

    @Column(name = "encounter_start_date")
    private LocalDate encounterStartDate;

    @Column(name = "encounter_end_date")
    private LocalDate encounterEndDate;

    @Column(name = "total_net", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "outcome", length = 100)
    private String outcome;

    @NotNull
    @Column(name = "is_cancelled", nullable = false)
    @Builder.Default
    private Boolean isCancelled = Boolean.FALSE;

    @Column(name = "cancel_reason", length = 50)
    private String cancelReason;

    @Column(name = "cancel_status", length = 100)
    private String cancelStatus;

    @Column(name = "cancel_outcome", length = 100)
    private String cancelOutcome;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "disposition", columnDefinition = "TEXT")
    private String disposition;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    @Column(name = "cancel_message", columnDefinition = "TEXT")
    private String cancelMessage;
    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "search_response_json", columnDefinition = "TEXT")
    private String searchResponseJson;

    @Column(name = "cancel_request_json", columnDefinition = "TEXT")
    private String cancelRequestJson;

    @Column(name = "cancel_response_json", columnDefinition = "TEXT")
    private String cancelResponseJson;
}