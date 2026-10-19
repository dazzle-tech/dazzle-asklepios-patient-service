package com.dazzle.asklepios.integration.nahdi.dto.prescription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response body of Nahdi {@code SendPrescription_clinics}, e.g.
 * {@code {"success":true,"error":[],"message":"","status":200,"OrderNumber":"22464879","online_order":null}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NahdiSendPrescriptionResponse(
        @JsonProperty("success") Boolean success,
        @JsonProperty("error") JsonNode error,
        @JsonProperty("message") String message,
        @JsonProperty("status") Integer status,
        @JsonProperty("OrderNumber") String orderNumber
) {}
