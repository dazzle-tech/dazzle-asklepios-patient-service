package com.dazzle.asklepios.integration.waseel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaseelEligibilityRejectionInterpreterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void memberMissingAtWaseelBecomesInsuranceNotFound() {
        EligibilityResponse response = response(
                "Failed",
                "error",
                "The provided member does not exist for the payer",
                List.of(Map.of(
                        "code", "1630",
                        "message", "Member does not exist"
                ))
        );

        Optional<WaseelEligibilityRejectionInterpreter.Rejection> rejection =
                WaseelEligibilityRejectionInterpreter.fromResponse(response, objectMapper);

        assertTrue(rejection.isPresent());
        assertEquals(
                WaseelEligibilityRejectionInterpreter.INSURANCE_NOT_FOUND_KEY,
                rejection.get().errorKey()
        );
        assertEquals(
                WaseelEligibilityRejectionInterpreter.INSURANCE_NOT_FOUND_MESSAGE,
                rejection.get().message()
        );
    }

    @Test
    void httpErrorForUnknownPayerBecomesInsuranceNotFound() {
        String body = """
                {
                  "message": "Bad Request",
                  "errors": [
                    {
                      "code": "IB-0001",
                      "message": "Payer not found",
                      "description": "The payer is not registered with Waseel"
                    }
                  ]
                }
                """;

        Optional<WaseelEligibilityRejectionInterpreter.Rejection> rejection =
                WaseelEligibilityRejectionInterpreter.insuranceNotFoundFromHttpBody(body, objectMapper);

        assertTrue(rejection.isPresent());
        assertEquals(
                WaseelEligibilityRejectionInterpreter.INSURANCE_NOT_FOUND_MESSAGE,
                rejection.get().message()
        );
        assertTrue(WaseelEligibilityRejectionInterpreter.describeHttpError(body, objectMapper)
                .contains("Payer not found"));
    }

    @Test
    void completedNotInForceResponseStaysAResult() {
        EligibilityResponse response = response(
                "OK",
                "complete",
                "Coverage is not in-force",
                null
        );

        assertFalse(WaseelEligibilityRejectionInterpreter.fromResponse(response, objectMapper).isPresent());
    }

    @Test
    void coverageThatSaysTheMemberIsMissingBecomesInsuranceNotFound() {
        EligibilityResponse response = new EligibilityResponse(
                null, null, null, null, null, null,
                "OK",
                "complete",
                null,
                null, null, null, null, null, null, null,
                List.of(new EligibilityCoverageDTO(
                        null, null, null, "false", "Member not found",
                        null, null, null, null, null, null, null, null, null, null, null, null
                )),
                null,
                null, null, null, null, null, null, null, null
        );

        Optional<WaseelEligibilityRejectionInterpreter.Rejection> rejection =
                WaseelEligibilityRejectionInterpreter.fromResponse(response, objectMapper);

        assertTrue(rejection.isPresent());
        assertEquals(
                WaseelEligibilityRejectionInterpreter.INSURANCE_NOT_FOUND_KEY,
                rejection.get().errorKey()
        );
    }

    @Test
    void otherWaseelRejectionKeepsTheWaseelReason() {
        EligibilityResponse response = response(
                "Failed",
                "error",
                "Missing marital status",
                null
        );

        Optional<WaseelEligibilityRejectionInterpreter.Rejection> rejection =
                WaseelEligibilityRejectionInterpreter.fromResponse(response, objectMapper);

        assertTrue(rejection.isPresent());
        assertEquals(WaseelEligibilityRejectionInterpreter.REJECTED_KEY, rejection.get().errorKey());
        assertEquals(
                "Waseel could not verify this insurance. Missing marital status",
                rejection.get().message()
        );
    }

    private static EligibilityResponse response(
            String status,
            String outcome,
            String disposition,
            Object errors
    ) {
        return new EligibilityResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                outcome,
                disposition,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                errors,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
