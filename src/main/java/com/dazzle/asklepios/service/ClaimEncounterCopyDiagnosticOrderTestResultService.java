package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimEncounterCopyDiagnosticOrderTestResultService {

    private final ClaimEncounterCopyDiagnosticOrderTestResultRepository
            claimEncounterCopyDiagnosticOrderTestResultRepository;

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    private final DiagnosticOrderRepository diagnosticOrderRepository;

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "claimEncounterCopyDiagnosticOrderTestResult",
                        "No authenticated user"
                ));
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopyDiagnosticOrderTestResult> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return claimEncounterCopyDiagnosticOrderTestResultRepository
                    .findAllByClaimEncounterCopyId(
                            claimEncounterCopyId
                    );
        }

        return claimEncounterCopyDiagnosticOrderTestResultRepository
                .findByClaimEncounterCopyIdAndStatusNot(
                        claimEncounterCopyId,
                        PatientHistoryStatus.CANCELLED
                );
    }

    public ClaimEncounterCopyDiagnosticOrderTestResult create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO dto
    ) {
        validate(dto.resultValueNumber(), dto.resultValueText());

        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findById(claimEncounterCopyId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim encounter copy not found with id "
                                + claimEncounterCopyId,
                        "claimEncounterCopyDiagnosticOrderTestResult",
                        "copy.notfound"
                ));

        DiagnosticOrderTest orderTest = requireLaboratoryTest(
                dto.orderTestId(),
                copy
        );

        ClaimEncounterCopyDiagnosticOrderTestResult entity =
                new ClaimEncounterCopyDiagnosticOrderTestResult();

        entity.setClaimEncounterCopyId(claimEncounterCopyId);
        entity.setOrderTestId(orderTest.getId());
        entity.setResultValueNumber(dto.resultValueNumber());
        entity.setResultValueText(dto.resultValueText());
        entity.setResultTypeAtEntry(dto.resultTypeAtEntry());
        entity.setStatus(PatientHistoryStatus.ACTIVE);

        return claimEncounterCopyDiagnosticOrderTestResultRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyDiagnosticOrderTestResult update(
            Long id,
            ClaimEncounterCopyDiagnosticOrderTestResultUpdateDTO dto
    ) {
        validate(dto.resultValueNumber(), dto.resultValueText());

        ClaimEncounterCopyDiagnosticOrderTestResult entity =
                claimEncounterCopyDiagnosticOrderTestResultRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy diagnostic order test result not found with id "
                                        + id,
                                "claimEncounterCopyDiagnosticOrderTestResult",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled diagnostic order test result cannot be updated.",
                    "claimEncounterCopyDiagnosticOrderTestResult",
                    "cancelled"
            );
        }

        entity.setResultValueNumber(dto.resultValueNumber());
        entity.setResultValueText(dto.resultValueText());

        return claimEncounterCopyDiagnosticOrderTestResultRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyDiagnosticOrderTestResult cancel(
            Long id,
            ClaimEncounterCopyDiagnosticOrderTestResultCancelDTO dto
    ) {
        ClaimEncounterCopyDiagnosticOrderTestResult entity =
                claimEncounterCopyDiagnosticOrderTestResultRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy diagnostic order test result not found with id "
                                        + id,
                                "claimEncounterCopyDiagnosticOrderTestResult",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Diagnostic order test result is already cancelled.",
                    "claimEncounterCopyDiagnosticOrderTestResult",
                    "already.cancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(
                dto.cancellationReason().trim()
        );

        return claimEncounterCopyDiagnosticOrderTestResultRepository
                .saveAndFlush(entity);
    }

    private DiagnosticOrderTest requireLaboratoryTest(
            Long orderTestId,
            ClaimEncounterCopy copy
    ) {
        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository
                .findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "claimEncounterCopyDiagnosticOrderTestResult",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        if (orderTest.getOrderType() != TestType.LABORATORY) {
            throw new BadRequestAlertException(
                    "not_laboratory_test",
                    "claimEncounterCopyDiagnosticOrderTestResult",
                    "DiagnosticOrderTest " + orderTestId + " is not a laboratory test"
            );
        }

        DiagnosticOrder order = diagnosticOrderRepository
                .findById(orderTest.getOrderId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "claimEncounterCopyDiagnosticOrderTestResult",
                        "DiagnosticOrder not found with id " + orderTest.getOrderId()
                ));

        if (!order.getEncounterId().equals(copy.getEncounterId())) {
            throw new BadRequestAlertException(
                    "encounter_mismatch",
                    "claimEncounterCopyDiagnosticOrderTestResult",
                    "DiagnosticOrderTest " + orderTestId + " does not belong to this encounter"
            );
        }

        return orderTest;
    }

    private void validate(
            java.math.BigDecimal resultValueNumber,
            String resultValueText
    ) {
        boolean hasNumber = resultValueNumber != null;
        boolean hasText = resultValueText != null && !resultValueText.trim().isEmpty();

        if (!hasNumber && !hasText) {
            throw new BadRequestAlertException(
                    "result_value_required",
                    "claimEncounterCopyDiagnosticOrderTestResult",
                    "Either resultValueNumber or resultValueText is required."
            );
        }
    }
}
