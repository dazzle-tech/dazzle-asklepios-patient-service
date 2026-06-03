package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EligibilityRequestResolverService {

    private final WaseelEligibilityRequestRepository eligibilityRequestRepository;

    public Long resolveLatestSuccessfulEligibilityId(PatientEncounter encounter) {
        if (encounter == null || encounter.getPatient() == null || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Patient not found on encounter",
                    "preAuthorization",
                    "patient.notFound"
            );
        }

        WaseelEligibilityRequest eligibility =
                eligibilityRequestRepository
                        .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                encounter.getPatient().getId(),
                                "SUCCESS"
                        )
                        .orElseThrow(() -> new BadRequestAlertException(
                                "No successful eligibility request found for patient",
                                "preAuthorization",
                                "eligibility.notFound"
                        ));

        return eligibility.getId();
    }
}