package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.ClaimSettlementGroupService;
import com.dazzle.asklepios.service.ClaimSettlementLogService;
import com.dazzle.asklepios.service.ClaimSettlementService;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementCreateRequest;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementGroupDetailResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementGroupSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementLogResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementRowResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementSubmitResponse;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;
import com.dazzle.asklepios.integration.waseel.dto.claim.PendingClaimInvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/claim-settlements")
public class ClaimSettlementController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimSettlementController.class);

    private final ClaimSettlementService claimSettlementService;
    private final ClaimSettlementGroupService claimSettlementGroupService;
    private final ClaimSettlementLogService claimSettlementLogService;

    @GetMapping("/numbers")
    public ResponseEntity<List<String>> settlementNumbers(
            @RequestParam(value = "payerNphiesId", required = false)
            String payerNphiesId,
            @RequestParam(value = "encounterType", required = false)
            String encounterType,
            @RequestParam(value = "fromDate", required = false)
            Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            Instant toDate
    ) {
        LOG.debug(
                "REST claim settlement numbers payerNphiesId={} encounterType={} fromDate={} toDate={}",
                payerNphiesId,
                encounterType,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(
                claimSettlementService.listSettlementNumbers(
                        payerNphiesId,
                        encounterType,
                        fromDate,
                        toDate
                )
        );
    }

    @GetMapping
    public ResponseEntity<Page<ClaimSettlementRowResponse>> search(
            @RequestParam(value = "payerNphiesId", required = false)
            String payerNphiesId,
            @RequestParam(value = "encounterType", required = false)
            String encounterType,
            @RequestParam(value = "fromDate", required = false)
            Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            Instant toDate,
            @RequestParam(value = "settlementNo", required = false)
            String settlementNo,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST claim settlements payerNphiesId={} encounterType={} fromDate={} toDate={} settlementNo={}",
                payerNphiesId,
                encounterType,
                fromDate,
                toDate,
                settlementNo
        );

        return ResponseEntity.ok(
                claimSettlementService.search(
                        payerNphiesId,
                        encounterType,
                        fromDate,
                        toDate,
                        settlementNo,
                        pageable
                )
        );
    }

    @GetMapping("/eligible-invoices")
    public ResponseEntity<Page<PendingClaimInvoiceResponse>> eligibleInvoices(
            @RequestParam(value = "payorId", required = false) Long payorId,
            @RequestParam(value = "payerNphiesId", required = false) String payerNphiesId,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(value = "encounterDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate encounterDateFrom,
            @RequestParam(value = "encounterDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate encounterDateTo,
            @RequestParam WaseelClaimType claimType,
            @RequestParam(value = "claimSubType", required = false) WaseelClaimSubType claimSubType,
            @RequestParam(value = "medicalRecordNumber", required = false) String medicalRecordNumber,
            @RequestParam(value = "encounterNumber", required = false) String encounterNumber,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST settlement eligible invoices payorId={} payerNphiesId={} claimType={} claimSubType={}",
                payorId,
                payerNphiesId,
                claimType,
                claimSubType
        );
        return ResponseEntity.ok(claimSettlementGroupService.eligibleInvoices(
                payorId,
                payerNphiesId,
                fromDate,
                toDate,
                encounterDateFrom,
                encounterDateTo,
                claimType,
                claimSubType,
                medicalRecordNumber,
                encounterNumber,
                pageable
        ));
    }

    @PostMapping("/groups")
    public ResponseEntity<ClaimSettlementGroupDetailResponse> createGroup(
            @RequestBody ClaimSettlementCreateRequest request
    ) {
        LOG.debug("REST create claim settlement group");
        return ResponseEntity.ok(claimSettlementGroupService.create(request));
    }

    @GetMapping("/groups")
    public ResponseEntity<Page<ClaimSettlementGroupSummaryResponse>> listGroups(
            @RequestParam(value = "payorId", required = false) Long payorId,
            @RequestParam(value = "payerNphiesId", required = false) String payerNphiesId,
            @RequestParam(value = "settlementNo", required = false) String settlementNo,
            @RequestParam(value = "status", required = false) ClaimSettlementStatus status,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(value = "submittableOnly", required = false, defaultValue = "false")
            boolean submittableOnly,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST claim settlement groups payorId={} settlementNo={} status={} submittableOnly={}",
                payorId,
                settlementNo,
                status,
                submittableOnly
        );
        return ResponseEntity.ok(claimSettlementGroupService.list(
                payorId,
                payerNphiesId,
                settlementNo,
                status,
                fromDate,
                toDate,
                submittableOnly,
                pageable
        ));
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<ClaimSettlementGroupDetailResponse> getGroup(@PathVariable Long id) {
        LOG.debug("REST claim settlement group id={}", id);
        return ResponseEntity.ok(claimSettlementGroupService.detail(id));
    }

    @GetMapping("/invoices/{financialDocumentId}/log")
    public ResponseEntity<List<ClaimSettlementLogResponse>> invoiceLog(@PathVariable Long financialDocumentId) {
        LOG.debug("REST claim settlement log financialDocumentId={}", financialDocumentId);
        return ResponseEntity.ok(claimSettlementLogService.findByInvoice(financialDocumentId));
    }

    @PostMapping("/groups/{id}/submit")
    public ResponseEntity<ClaimSettlementSubmitResponse> submitGroup(@PathVariable Long id) {
        LOG.debug("REST submit claim settlement group id={}", id);
        return ResponseEntity.ok(claimSettlementGroupService.submit(id));
    }
}
