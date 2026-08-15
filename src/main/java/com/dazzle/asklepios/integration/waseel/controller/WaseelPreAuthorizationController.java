package com.dazzle.asklepios.integration.waseel.controller;


import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationCommunicationHistoryResponse;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.EncounterPreAuthorizationRefreshResponse;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationCommunicationHistoryService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationRejectedItemService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResubmissionService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationStatusRefreshService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationTrackingService;
import com.dazzle.asklepios.integration.waseel.service.WaseelMockPreAuthStatus;
import com.dazzle.asklepios.integration.waseel.service.WaseelPreAuthorizationMockService;
import com.dazzle.asklepios.integration.waseel.service.WaseelPreAuthorizationService;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class WaseelPreAuthorizationController {

    private final WaseelPreAuthorizationService service;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationTrackingService trackingService;
    private final PreAuthorizationCommunicationHistoryService communicationHistoryService;
    private final PreAuthorizationStatusRefreshService preAuthorizationStatusRefreshService;
    private final PreAuthorizationRejectedItemService preAuthorizationRejectedItemService;
    private final PreAuthorizationResubmissionService preAuthorizationResubmissionService;
    private final WaseelPreAuthorizationMockService mockService;

    @GetMapping("/internal/waseel/pre-authorizations/tracking")
    public Page<PreAuthorizationTrackingResponse> getAll(Pageable pageable) {
        return trackingService.findAll(pageable);
    }

    @GetMapping("/internal/waseel/pre-authorizations/tracking/{id}")
    public PreAuthorizationTrackingResponse getOne(@PathVariable Long id) {
        return trackingService.findById(id);
    }

    @GetMapping("/internal/waseel/pre-authorizations/{preAuthorizationId}/communications")
    public List<PreAuthorizationCommunicationHistoryResponse> getCommunications(
            @PathVariable Long preAuthorizationId
    ) {
        return communicationHistoryService.list(preAuthorizationId);
    }

    @GetMapping("/internal/waseel/pre-authorizations/search")
    public Object search(
            @RequestParam(value = "preAuthorizationId", required = false) Long preAuthorizationId,
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

    @PostMapping("/internal/waseel/encounters/{encounterId}/pre-authorization/refresh")
    public EncounterPreAuthorizationRefreshResponse refreshEncounterPreAuthorization(
            @PathVariable Long encounterId
    ) {
        return preAuthorizationStatusRefreshService.refreshEncounter(encounterId);
    }

    @PostMapping("/internal/waseel/encounters/{encounterId}/pre-authorization/refresh-local")
    public EncounterPreAuthorizationRefreshResponse refreshEncounterPreAuthorizationLocal(
            @PathVariable Long encounterId
    ) {
        return preAuthorizationStatusRefreshService.refreshEncounterLocalOnly(encounterId);
    }

    /**
     * Rebuilds the payload from current pricing and sends a new Waseel request.
     * Shown when {@code canResubmit} is true (gateway/validation failure, not a payer decision).
     */
    @PostMapping("/internal/waseel/pre-authorizations/{id}/resubmit")
    public PreAuthorizationTrackingResponse resubmitPreAuthorization(@PathVariable Long id) {
        return preAuthorizationResubmissionService.resubmit(id);
    }

    @PostMapping("/internal/waseel/encounters/{encounterId}/pre-authorization/items/{patientServiceProductId}/pay-as-cash")
    public BillingOperationResult payRejectedPreAuthorizationItemAsCash(
            @PathVariable Long encounterId,
            @PathVariable Long patientServiceProductId
    ) {
        return preAuthorizationRejectedItemService.payRejectedItemAsCash(
                encounterId,
                patientServiceProductId
        );
    }

    @PostMapping("/internal/waseel/encounters/{encounterId}/pre-authorization/items/{patientServiceProductId}/clone")
    public void cloneRejectedPreAuthorizationItem(
            @PathVariable Long encounterId,
            @PathVariable Long patientServiceProductId
    ) {
        preAuthorizationRejectedItemService.clonePreAuthorization(
                encounterId,
                patientServiceProductId
        );
    }

    /**
     * Internal/admin sync only. Normal flows submit pre-authorization
     * automatically from the backend when items are added or eligibility succeeds.
     */
    @PostMapping("/internal/waseel/encounters/{encounterId}/pre-authorization/sync")
    public void syncEncounterPreAuthorization(@PathVariable Long encounterId) {
        encounterPreAuthorizationSyncService.syncEncounter(encounterId);
    }

    /**
     * Inspect mock mode for local pre-auth testing.
     */
    @GetMapping("/internal/waseel/mock/pre-authorization")
    public Map<String, Object> getMockPreAuthorizationStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", mockService.isEnabled());
        body.put("status", mockService.currentStatus().waseelStatus());
        body.put("allowedStatuses", List.of("approved", "rejected", "partial", "pended"));
        return body;
    }

    /**
     * Change mock search outcome at runtime (no restart).
     * Example: PUT .../mock/pre-authorization?status=partial
     */
    @PutMapping("/internal/waseel/mock/pre-authorization")
    public Map<String, Object> setMockPreAuthorizationStatus(@RequestParam("status") String status) {
        mockService.setRuntimeStatus(WaseelMockPreAuthStatus.from(status));
        return getMockPreAuthorizationStatus();
    }

    /**
     * Clear runtime override and fall back to waseel.api.mock-pre-auth-status from YAML.
     */
    @DeleteMapping("/internal/waseel/mock/pre-authorization")
    public Map<String, Object> clearMockPreAuthorizationStatusOverride() {
        mockService.setRuntimeStatus(null);
        return getMockPreAuthorizationStatus();
    }
}