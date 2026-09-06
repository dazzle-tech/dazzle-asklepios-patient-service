package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.FlexibleLongDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaseelClaimUploadResponse(
        @JsonAlias({"transactionLogId", "transactionLogID"})
        @JsonDeserialize(using = FlexibleLongDeserializer.class)
        Long transcationLogId,
        String message,
        @JsonDeserialize(using = FlexibleLongDeserializer.class)
        Long uploadId,
        @JsonDeserialize(using = FlexibleLongDeserializer.class)
        Long providerId,
        String uploadName,
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime uploadDate,
        Integer noOfNotUploadedClaims,
        Integer noOfUploadedClaims,
        BigDecimal totalAmtOfUploadedClaims,
        Integer noOfAcceptedClaims,
        BigDecimal totalAmtOfAcceptedClaims,
        Integer noOfNotAcceptedClaims,
        BigDecimal totalAmtOfNotAcceptedClaims,
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime lastModifiedDate,
        BigDecimal ratioOfAccepted,
        BigDecimal ratioOfNotAccepted
) {}
