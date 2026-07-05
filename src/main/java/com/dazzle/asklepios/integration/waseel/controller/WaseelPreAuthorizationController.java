package com.dazzle.asklepios.integration.waseel.controller;


import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationTrackingService;
import com.dazzle.asklepios.integration.waseel.service.WaseelPreAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class WaseelPreAuthorizationController {

    private final WaseelPreAuthorizationService service;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final PreAuthorizationTrackingService trackingService;

    @GetMapping("/internal/waseel/pre-authorizations/tracking")
    public Page<PreAuthorizationTrackingResponse> getAll(Pageable pageable) {
        return trackingService.findAll(pageable);
    }

    @GetMapping("/internal/waseel/pre-authorizations/tracking/{id}")
    public PreAuthorizationTrackingResponse getOne(@PathVariable Long id) {
        return trackingService.findById(id);
    }

    @GetMapping("/internal/waseel/pre-authorizations/search")
    public Object search(
            @RequestParam("preAuthorizationId") Long preAuthorizationId,
            @RequestParam("requestId") Long requestId
    ) {
        return service.searchAndUpdate(preAuthorizationId, requestId);
    }
    @PostMapping("/internal/waseel/pre-authorizations/communication")
    public Object communicate(@RequestBody PreAuthorizationCommunicationRequest request) {
        return service.communicate(request);
    }

    @PostMapping("/internal/waseel/pre-authorizations/cancel")
    public Object cancel(@RequestBody PreAuthorizationCancelRequest request) {
        return service.cancel(request);
    }


    @PostMapping("/internal/waseel/encounters/{encounterId}/eligibility")
    public EligibilityCheckResponse checkEncounterEligibility(@PathVariable Long encounterId) {
        return encounterInsuranceEligibilityService.checkEncounterEligibility(encounterId);
    }
}