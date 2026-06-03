package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalEligibilitySnapshotMapper;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalEligibilitySnapshotService {

    private final WaseelEligibilityRequestRepository eligibilityRequestRepository;
    private final ApprovalEligibilitySnapshotMapper mapper;

    @Transactional(readOnly = true)
    public WaseelApprovalEligibilitySnapshot buildSnapshot(Long eligibilityRequestId) {
        WaseelEligibilityRequest eligibilityRequest = eligibilityRequestRepository
                .findById(eligibilityRequestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Eligibility request not found",
                        "preAuthorization",
                        "eligibility.notFound"
                ));

        return mapper.fromEligibilityRequest(eligibilityRequest);
    }
}