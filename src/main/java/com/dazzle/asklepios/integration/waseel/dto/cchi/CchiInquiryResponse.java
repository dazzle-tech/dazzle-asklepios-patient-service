package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiInquiryResponse(
        String apiStatus,
        String statusCode,
        String message,
        Boolean fromWaseelDB,
        CchiBeneficiaryData data
) {}