package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.AgeGroupClient;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalEligibilitySnapshotMapper;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalEligibilitySnapshotService {

    private final WaseelEligibilityRequestRepository eligibilityRequestRepository;
    private final ApprovalEligibilitySnapshotMapper mapper;
    private final AgeGroupClient ageGroupClient;

    @Transactional(readOnly = true)
    public WaseelApprovalEligibilitySnapshot buildSnapshot(Long eligibilityRequestId) {
        WaseelEligibilityRequest eligibilityRequest = eligibilityRequestRepository
                .findById(eligibilityRequestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Eligibility request not found",
                        "preAuthorization",
                        "eligibility.notFound"
                ));

        WaseelApprovalEligibilitySnapshot snapshot =
                mapper.fromEligibilityRequest(eligibilityRequest);

        Boolean isNewBorn = resolveIsNewBorn(snapshot);

        return new WaseelApprovalEligibilitySnapshot(
                snapshot.transfer(),
                isNewBorn,
                snapshot.beneficiary(),
                snapshot.insurancePlan(),
                snapshot.memberId(),
                snapshot.patientInsuranceId(),
                snapshot.payorId(),
                snapshot.payorPlanId(),
                snapshot.providerId(),
                snapshot.destinationId(),
                snapshot.eligibilityResponseId(),
                snapshot.eligibilityResponseUrl()
        );
    }

    private Boolean resolveIsNewBorn(WaseelApprovalEligibilitySnapshot snapshot) {
        if (snapshot == null
                || snapshot.beneficiary() == null
                || snapshot.beneficiary().dob() == null
                || snapshot.beneficiary().dob().isBlank()) {
            return Boolean.FALSE;
        }

        try {
            LocalDate dob = LocalDate.parse(snapshot.beneficiary().dob());

            return Boolean.TRUE.equals(
                    ageGroupClient.isNewBornByBirthDate(dob)
            );
        } catch (Exception ex) {
            return Boolean.FALSE;
        }
    }
}