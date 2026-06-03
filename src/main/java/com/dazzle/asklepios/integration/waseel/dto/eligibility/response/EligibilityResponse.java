package com.dazzle.asklepios.integration.waseel.dto.eligibility.response;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityResponse(
        Long transactionId,
        Long responseId,
        String outgoingTransactionId,
        String eligibilityRequestId,
        String beneficiaryName,
        String subscriberName,
        String status,
        String outcome,
        String disposition,
        String serviceDate,
        String transactionDate,
        String nphiesResponseId,
        Boolean transfer,
        String siteEligibility,
        Boolean isNewBorn,
        List<String> purpose,
        List<EligibilityCoverageDTO> coverages,
        Object errors,
        String eligibilityIdentifierUrl,
        String documentId,
        String documentType,
        String payerId,
        Boolean isEmergency,
        String requestBundleId,
        String responseBundleId,
        String tpa_Id
) {}