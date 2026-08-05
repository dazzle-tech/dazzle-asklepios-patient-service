package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.BillingEligibilitySnapshotService;
import com.dazzle.asklepios.service.InvoiceGenerationService;
import com.dazzle.asklepios.service.dto.billing.BillableVisitResponse;
import com.dazzle.asklepios.service.dto.billing.BillingEligibilitySnapshotResponse;
import com.dazzle.asklepios.service.dto.billing.EncounterInvoiceDetailsResponse;
import com.dazzle.asklepios.service.dto.billing.FinancialCloseRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialCloseResult;
import com.dazzle.asklepios.service.dto.billing.FreezeEligibilitySnapshotRequest;
import com.dazzle.asklepios.service.dto.billing.GenerateInvoiceRequest;
import com.dazzle.asklepios.service.dto.billing.GenerateInvoiceResult;
import com.dazzle.asklepios.service.dto.billing.PatientFinancialDocumentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/invoice-generation")
public class InvoiceGenerationController {

    private static final Logger LOG =
            LoggerFactory.getLogger(InvoiceGenerationController.class);

    private final InvoiceGenerationService invoiceGenerationService;
    private final BillingEligibilitySnapshotService billingEligibilitySnapshotService;

    @GetMapping("/patients/{patientId}/billable-visits")
    public ResponseEntity<List<BillableVisitResponse>> getBillableVisits(
            @PathVariable("patientId") @NotNull Long patientId
    ) {
        LOG.debug(
                "REST request billable visits patientId={}",
                patientId
        );

        return ResponseEntity.ok(
                invoiceGenerationService.findBillableVisits(patientId)
        );
    }

    @GetMapping("/encounters/{encounterId}/details")
    public ResponseEntity<EncounterInvoiceDetailsResponse> getEncounterInvoiceDetails(
            @PathVariable("encounterId") @NotNull Long encounterId
    ) {
        LOG.debug(
                "REST request encounter invoice details encounterId={}",
                encounterId
        );

        return ResponseEntity.ok(
                invoiceGenerationService.getEncounterInvoiceDetails(encounterId)
        );
    }

    @GetMapping("/patients/{patientId}/invoices")
    public ResponseEntity<List<PatientFinancialDocumentResponse>> getPatientInvoices(
            @PathVariable("patientId") @NotNull Long patientId
    ) {
        LOG.debug(
                "REST request patient financial invoices patientId={}",
                patientId
        );

        return ResponseEntity.ok(
                invoiceGenerationService.listPatientInvoices(patientId)
        );
    }

    @GetMapping("/patients/{patientId}/documents")
    public ResponseEntity<List<PatientFinancialDocumentResponse>> getPatientFinancialDocuments(
            @PathVariable("patientId") @NotNull Long patientId
    ) {
        LOG.debug(
                "REST request patient financial documents patientId={}",
                patientId
        );

        return ResponseEntity.ok(
                invoiceGenerationService.listPatientFinancialDocuments(patientId)
        );
    }

    @PostMapping("/encounters/{encounterId}/financial-close")
    public ResponseEntity<FinancialCloseResult> financialClose(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody @NotNull FinancialCloseRequest request
    ) {
        LOG.debug(
                "REST request financial close encounterId={} requestId={}",
                encounterId,
                request.requestId()
        );

        return ResponseEntity.ok(
                invoiceGenerationService.financialClose(
                        encounterId,
                        request
                )
        );
    }

    @PostMapping("/encounters/{encounterId}/generate-invoices")
    public ResponseEntity<GenerateInvoiceResult> generateInvoices(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody @NotNull GenerateInvoiceRequest request
    ) {
        LOG.debug(
                "REST request generate invoices encounterId={} requestId={}",
                encounterId,
                request.requestId()
        );

        return ResponseEntity.ok(
                invoiceGenerationService.generateInvoices(
                        encounterId,
                        request
                )
        );
    }

    @GetMapping("/encounters/{encounterId}/eligibility-snapshot")
    public ResponseEntity<BillingEligibilitySnapshotResponse> getEligibilitySnapshot(
            @PathVariable("encounterId") @NotNull Long encounterId
    ) {
        LOG.debug(
                "REST request eligibility snapshot encounterId={}",
                encounterId
        );

        return billingEligibilitySnapshotService.findByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/encounters/{encounterId}/eligibility-snapshot")
    public ResponseEntity<BillingEligibilitySnapshotResponse> freezeEligibilitySnapshot(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody @NotNull FreezeEligibilitySnapshotRequest request
    ) {
        LOG.debug(
                "REST request freeze eligibility snapshot encounterId={} requestId={}",
                encounterId,
                request.requestId()
        );

        return ResponseEntity.ok(
                billingEligibilitySnapshotService.freezeForEncounter(
                        encounterId,
                        request
                )
        );
    }
}
