package com.dazzle.asklepios.integration.waseel.controller;

import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiFetchInsuranceRequest;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiFetchPatientResponse;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiMappedPatientResponse;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiPatientDocumentOption;
import com.dazzle.asklepios.integration.waseel.service.WaseelCchiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class WaseelCchiController {

    private final WaseelCchiService waseelCchiService;

    public WaseelCchiController(WaseelCchiService waseelCchiService) {
        this.waseelCchiService = waseelCchiService;
    }

    @GetMapping("/cchi/{documentId}/fetch")
    public ResponseEntity<CchiFetchPatientResponse> fetchPatientForRegistration(
            @PathVariable String documentId
    ) {
        return ResponseEntity.ok(waseelCchiService.fetchPatientForRegistration(documentId));
    }

    @GetMapping("/{patientId}/cchi/insurance-document-options")
    public ResponseEntity<List<CchiPatientDocumentOption>> getInsuranceDocumentOptions(
            @PathVariable Long patientId
    ) {
        return ResponseEntity.ok(waseelCchiService.listInsuranceFetchDocumentOptions(patientId));
    }

    @PostMapping("/{patientId}/cchi/fetch-insurance")
    public ResponseEntity<CchiMappedPatientResponse> fetchInsuranceFromCchi(
            @PathVariable Long patientId,
            @RequestBody(required = false) CchiFetchInsuranceRequest request
    ) {
        String documentId = request == null ? null : request.documentId();
        return ResponseEntity.ok(waseelCchiService.fetchInsuranceForPatient(patientId, documentId));
    }

    @PostMapping("/{patientId}/cchi/refresh")
    public ResponseEntity<CchiMappedPatientResponse> refreshPatientFromCchi(
            @PathVariable Long patientId
    ) {
        return ResponseEntity.ok(waseelCchiService.refreshPatientFromCchi(patientId));
    }
}
