package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.PatientPaymentsService;
import com.dazzle.asklepios.service.dto.patientPayments.InsuranceAmountDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientLedgerSummaryDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentCreateDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentDetailsDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentFormDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentServiceItemDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientPaymentsController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPaymentsController.class);

    private final PatientPaymentsService patientPaymentsService;
    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;
    private final WaseelCoverageExtractionService coverageExtractionService;

    public PatientPaymentsController(PatientPaymentsService patientPaymentsService, WaseelEligibilityRequestRepository waseelEligibilityRequestRepository, WaseelCoverageExtractionService coverageExtractionService) {
        this.patientPaymentsService = patientPaymentsService;
        this.waseelEligibilityRequestRepository = waseelEligibilityRequestRepository;
        this.coverageExtractionService = coverageExtractionService;
    }

    @PostMapping("/payment")
    public ResponseEntity<PatientPaymentDetailsDTO> create(
            @Valid @RequestBody @NotNull PatientPaymentCreateDTO dto
    ) {
        LOG.debug("REST create PatientPayments payload={}", dto);

        if (dto.patientId() == null) {
            LOG.warn("[CREATE] PatientPayments rejected: patientId is null payload={}", dto);
            throw new BadRequestAlertException("patientId is required", "patientPayments", "patient.required");
        }
        if (dto.encounterId() == null) {
            LOG.warn("[CREATE] PatientPayments rejected: encounterId is null payload={}", dto);
            throw new BadRequestAlertException("encounterId is required", "patientPayments", "encounter.required");
        }


        PatientPaymentDetailsDTO result = patientPaymentsService.create(dto);

        LOG.debug("REST create PatientPayments success paymentId={} patientId={} encounterId={}",
                result.id(), result.patientId(), result.encounterId());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/payment/patient/{patientId}/balance")
    public ResponseEntity<BigDecimal> getPatientBalance(
            @PathVariable @NotNull Long patientId
    ) {
        LOG.debug("REST get Patient balance patientId={}", patientId);

        BigDecimal balance = patientPaymentsService.getPatientBalance(patientId);

        LOG.debug("REST get Patient balance result patientId={} balance={}", patientId, balance);

        return ResponseEntity.ok(balance);
    }

    @GetMapping("/payment/patient/{patientId}/ledger-summary")
    public ResponseEntity<PatientLedgerSummaryDTO> getPatientLedgerSummary(
            @PathVariable @NotNull Long patientId
    ) {
        LOG.debug("REST get Patient ledger summary patientId={}", patientId);

        PatientLedgerSummaryDTO summary = patientPaymentsService.getPatientLedgerSummary(patientId);

        LOG.debug("REST get Patient ledger summary result patientId={} totalDebt={} walletBalance={}",
                summary.patientId(), summary.totalDebt(), summary.walletBalance());

        return ResponseEntity.ok(summary);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET Payment by Encounter ID
    // GET /api/patient/encounter/{encounterId}/payment
    // Returns 200 + payment data if found, 204 if no payment yet (normal for new encounters)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/encounter/{encounterId}/payment")
    public ResponseEntity<PatientPaymentFormDTO> getPaymentByEncounter(
            @PathVariable @NotNull Long encounterId
    ) {
        LOG.debug("REST get payment by encounterId={}", encounterId);

        try {
            PatientPaymentFormDTO result =
                    patientPaymentsService.getPaymentByEncounter(encounterId);

            return ResponseEntity.ok(result);

        } catch (NotFoundAlertException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/insurance/calculate")
    public InsuranceAmountDTO calculateInsuranceAmount(@RequestBody PatientPaymentCreateDTO dto) {

        List<PatientPaymentServiceItemDTO> services =
                dto.services() == null ? List.of() : dto.services();

        BigDecimal totalDue = services.stream()
                .filter(s -> !Boolean.TRUE.equals(s.isExempted()))
                .map(this::resolveServiceAmountFromDTO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WaseelEligibilityRequest eligibility =
                waseelEligibilityRequestRepository
                        .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                dto.patientId(),
                                "SUCCESS"
                        )
                        .orElseThrow(() -> new IllegalStateException("No valid eligibility"));

        InsuranceCoverage coverage =
                coverageExtractionService.extractCoverage(eligibility.getResponseJson());

        BigDecimal totalPatientShare = BigDecimal.ZERO;

        for (PatientPaymentServiceItemDTO service : services) {

            BigDecimal net = resolveServiceAmountFromDTO(service);

            BigDecimal copay =
                    net.multiply(coverage.getCopaymentPercent())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                            .min(coverage.getCopaymentCap());

            totalPatientShare = totalPatientShare.add(copay);
        }

        return new InsuranceAmountDTO(
                totalDue,
                totalPatientShare,
                totalDue.subtract(totalPatientShare)
        );
    }
    private BigDecimal resolveServiceAmountFromDTO(PatientPaymentServiceItemDTO item) {

        BigDecimal unitPrice = item.price() == null
                ? BigDecimal.ZERO
                : item.price();

        BigDecimal quantity = BigDecimal.ONE;

        return unitPrice.multiply(quantity);
    }

}