package com.dazzle.asklepios.integration.waseel.controller;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiMappedPatientResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.integration.waseel.service.ApprovalEligibilitySnapshotService;
import com.dazzle.asklepios.integration.waseel.service.ApprovalRequestBuilderService;
import com.dazzle.asklepios.integration.waseel.service.WaseelApprovalService;
import com.dazzle.asklepios.integration.waseel.service.WaseelCchiService;
import com.dazzle.asklepios.integration.waseel.service.WaseelEligibilityCheckService;
import com.dazzle.asklepios.integration.waseel.service.WaseelEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.WaseelTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient")
public class WaseelTestController {

    private final WaseelTokenService waseelTokenService;
    private final WaseelCchiService waseelCchiService;
    private final WaseelEligibilityService waseelEligibilityService;
    private final WaseelApprovalService waseelApprovalService;
    private final WaseelEligibilityCheckService waseelEligibilityCheckService;
    private final ApprovalEligibilitySnapshotService snapshotService;
    private final ApprovalRequestBuilderService builderService;

    public WaseelTestController(
            WaseelTokenService waseelTokenService,
            WaseelCchiService waseelCchiService,
            WaseelEligibilityService waseelEligibilityService,
            WaseelApprovalService waseelApprovalService,
            WaseelEligibilityCheckService waseelEligibilityCheckService, ApprovalEligibilitySnapshotService snapshotService, ApprovalRequestBuilderService builderService) {
        this.waseelTokenService = waseelTokenService;
        this.waseelCchiService = waseelCchiService;
        this.waseelEligibilityService = waseelEligibilityService;
        this.waseelApprovalService = waseelApprovalService;
        this.waseelEligibilityCheckService = waseelEligibilityCheckService;
        this.snapshotService = snapshotService;
        this.builderService = builderService;
    }

    @GetMapping("/internal/waseel/token-test")
    public String testWaseelToken() {
        return waseelTokenService.getToken() != null ? "Waseel token received" : "No token";
    }

    @GetMapping("/internal/waseel/cchi/{documentId}")
    public CchiInquiryResponse testCchi(@PathVariable String documentId) {
        return waseelCchiService.fetchBeneficiaryByDocumentId(documentId);
    }

    @GetMapping("/internal/waseel/cchi/{documentId}/patient")
    public Patient getMappedPatientFromCchi(@PathVariable String documentId) {
        return waseelCchiService.fetchPatientByDocumentId(documentId);
    }

    @PostMapping("/internal/waseel/eligibility")
    public EligibilityResponse requestEligibility(@RequestBody EligibilityRequest request) {
        return waseelEligibilityService.requestEligibility(request);
    }

    @PostMapping("/internal/waseel/approval")
    public ApprovalResponse requestApproval(@RequestBody ApprovalRequest request) {
        return waseelApprovalService.requestApproval(request);
    }

    @GetMapping("/internal/waseel/approval/{requestId}")
    public ApprovalResponse getExternalApproval(@PathVariable String requestId) {
        return waseelApprovalService.getExternalApproval(requestId);
    }

    @PostMapping("/internal/waseel/approval/cancel")
    public ApprovalResponse cancelApproval(@RequestBody ApprovalCancelRequest request) {
        return waseelApprovalService.cancelApproval(request);
    }
    @GetMapping("/internal/waseel/cchi/{documentId}/mapped")
    public CchiMappedPatientResponse getMappedPatientWithAddressFromCchi(
            @PathVariable String documentId
    ) {
        return waseelCchiService.fetchMappedPatientByDocumentId(documentId);
    }

    @PostMapping("/internal/waseel/eligibility/check")
    public EligibilityCheckResponse checkEligibility(
            @RequestBody EligibilityCheckRequest request
    ) {
        return waseelEligibilityCheckService.checkEligibility(request);
    }


    @GetMapping("/eligibility/{eligibilityRequestId}/snapshot")
    public ResponseEntity<WaseelApprovalEligibilitySnapshot> getSnapshot(
            @PathVariable Long eligibilityRequestId
    ) {
        return ResponseEntity.ok(snapshotService.buildSnapshot(eligibilityRequestId));
    }
    @GetMapping("/build")
    public ResponseEntity<WaseelApprovalRequest> buildRequest(
            @RequestParam Long eligibilityRequestId,
            @RequestParam Long encounterId
    ) {
        return ResponseEntity.ok(
                builderService.buildRequest(eligibilityRequestId, encounterId)
        );
    }

}