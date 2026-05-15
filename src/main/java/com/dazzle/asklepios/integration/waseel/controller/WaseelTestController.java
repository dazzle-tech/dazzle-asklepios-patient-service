package com.dazzle.asklepios.integration.waseel.controller;

import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityResponse;
import com.dazzle.asklepios.integration.waseel.service.WaseelCchiService;
import com.dazzle.asklepios.integration.waseel.service.WaseelEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.WaseelTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.service.WaseelApprovalService;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCancelRequest;

@RestController
@RequestMapping("/api/patient")
public class WaseelTestController {

    private final WaseelTokenService waseelTokenService;
    private final WaseelCchiService waseelCchiService;
    private final WaseelEligibilityService waseelEligibilityService;
    private final WaseelApprovalService waseelApprovalService;
    public WaseelTestController(
            WaseelTokenService waseelTokenService,
            WaseelCchiService waseelCchiService, WaseelEligibilityService waseelEligibilityService, WaseelApprovalService waseelApprovalService
    ) {
        this.waseelTokenService = waseelTokenService;
        this.waseelCchiService = waseelCchiService;
        this.waseelEligibilityService = waseelEligibilityService;
        this.waseelApprovalService = waseelApprovalService;
    }

    @GetMapping("/internal/waseel/token-test")
    public String testWaseelToken() {
        return waseelTokenService.getToken() != null ? "Waseel token received" : "No token";
    }

    @GetMapping("/internal/waseel/cchi/{documentId}")
    public CchiInquiryResponse testCchi(@PathVariable String documentId) {
        return waseelCchiService.fetchBeneficiaryByDocumentId(documentId);
    }

    @PostMapping("/internal/waseel/eligibility")
    public EligibilityResponse requestEligibility(
            @RequestBody EligibilityRequest request
    ) {
        return waseelEligibilityService.requestEligibility(request);
    }
    @PostMapping("/internal/waseel/approval")
    public ApprovalResponse requestApproval(@RequestBody ApprovalRequest request) {
        return waseelApprovalService.requestApproval(request);
    }

    @GetMapping("/internal/waseel/approval/{requestId}")
    public ApprovalResponse getExternalApproval(
            @PathVariable String requestId
    ) {
        return waseelApprovalService.getExternalApproval(requestId);
    }
    @PostMapping("/internal/waseel/approval/cancel")
    public ApprovalResponse cancelApproval(
            @RequestBody ApprovalCancelRequest request
    ) {
        return waseelApprovalService.cancelApproval(request);
    }

}