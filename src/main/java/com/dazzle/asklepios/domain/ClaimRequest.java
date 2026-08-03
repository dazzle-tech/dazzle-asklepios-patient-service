package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "claim_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "financial_document_id", nullable = false)
    private Long financialDocumentId;

    @Column(name = "pre_authorization_id")
    private Long preAuthorizationId;

    @Column(name = "patient_insurance_id")
    private Long patientInsuranceId;

    @Column(name = "upload_name", length = 200)
    private String uploadName;

    @Column(name = "upload_id")
    private Long uploadId;

    @Column(name = "prov_claim_no", length = 200)
    private String provClaimNo;

    @Column(name = "claim_reference", length = 200)
    private String claimReference;

    @Column(name = "pre_auth_ref_no", length = 500)
    private String preAuthRefNo;

    @Column(name = "approval_response_id")
    private Long approvalResponseId;

    @Column(name = "total_net", precision = 19, scale = 2)
    private BigDecimal totalNet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ClaimStatus status;

    @Column(name = "outcome", length = 100)
    private String outcome;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
