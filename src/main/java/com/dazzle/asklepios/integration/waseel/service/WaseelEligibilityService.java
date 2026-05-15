package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelEligibilityClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiary;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageClass;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityTestRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WaseelEligibilityService {

    private final WaseelEligibilityClient eligibilityClient;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;

    public WaseelEligibilityService(
            WaseelEligibilityClient eligibilityClient,
            WaseelTokenService tokenService,
            WaseelApiProperties properties
    ) {
        this.eligibilityClient = eligibilityClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public EligibilityResponse checkEligibility(EligibilityTestRequest testRequest) {
        String token = tokenService.getToken();

        EligibilityRequest request = buildTestEligibilityRequest(testRequest);

        return eligibilityClient.checkEligibility(
                "Bearer " + token.trim(),
                properties.providerId().trim(),
                request
        );
    }

    private EligibilityRequest buildTestEligibilityRequest(EligibilityTestRequest testRequest) {
        EligibilityCoverageClass coverageClass = new EligibilityCoverageClass(
                "plan",
                "a",
                ""
        );

        EligibilityInsurancePlan insurancePlan = new EligibilityInsurancePlan(
                "3793",
                "INS-FHIR",
                "Insurance Company Testing Payer",
                "12121212",
                "357159456",
                "INS-FHIR",
                "-1",
                "2028-09-28",
                "other",
                "EHCPOL",
                null,
                null,
                List.of(coverageClass),
                "123456789",
                true
        );

        EligibilityBeneficiary beneficiary = new EligibilityBeneficiary(
                3583L,
                "Akhil Nair",
                "11111111",
                "PPN",
                "Akhil",
                null,
                null,
                null,
                "Akhil Nair",
                "2001-11-14T00:00:00.000+0300",
                "MALE",
                null,
                null,
                null,
                null,
                "O+",
                null,
                null,
                null,
                "U",
                null,
                "unknown",
                null,
                "home",
                "home",
                "mexico",
                "albama",
                null,
                "123123",
                null,
                List.of(insurancePlan),
                true
        );

        return new EligibilityRequest(
                false,
                beneficiary,
                null,
                insurancePlan,
                testRequest.serviceDate(),
                null,
                false,
                false,
                true,
                false,
                Boolean.TRUE.equals(testRequest.isEmergency()),
                Boolean.TRUE.equals(testRequest.referral()),
                Map.of(),
                "-1"
        );
    }
}