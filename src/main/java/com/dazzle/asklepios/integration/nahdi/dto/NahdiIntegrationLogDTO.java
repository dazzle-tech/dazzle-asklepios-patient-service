package com.dazzle.asklepios.integration.nahdi.dto;

import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog}.
 */
public record NahdiIntegrationLogDTO(
        Long id,
        String endpoint,
        String requestPayload,
        String responsePayload,
        NahdiIntegrationStatus status,
        String orderNumber,
        Instant createdDate
) implements Serializable {}
