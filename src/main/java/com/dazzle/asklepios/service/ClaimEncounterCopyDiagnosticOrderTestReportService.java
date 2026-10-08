package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportUpdateDTO;
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
public class ClaimEncounterCopyDiagnosticOrderTestReportService {

    private final ClaimEncounterCopyDiagnosticOrderTestReportRepository
            claimEncounterCopyDiagnosticOrderTestReportRepository;

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    private final DiagnosticOrderRepository diagnosticOrderRepository;

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "claimEncounterCopyDiagnosticOrderTestReport",
                        "No authenticated user"
                ));
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopyDiagnosticOrderTestReport> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return claimEncounterCopyDiagnosticOrderTestReportRepository
                    .findAllByClaimEncounterCopyId(
                            claimEncounterCopyId
                    );
        }

        return claimEncounterCopyDiagnosticOrderTestReportRepository
                .findByClaimEncounterCopyIdAndStatusNot(
                        claimEncounterCopyId,
                        PatientHistoryStatus.CANCELLED
                );
    }

    public ClaimEncounterCopyDiagnosticOrderTestReport create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyDiagnosticOrderTestReportCreateDTO dto
    ) {
        validate(
                dto.report(),
                dto.radiologistInformation(),
                dto.criticalFindings(),
                dto.radiologistComments()
        );

        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findById(claimEncounterCopyId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim encounter copy not found with id "
                                + claimEncounterCopyId,
                        "claimEncounterCopyDiagnosticOrderTestReport",
                        "copy.notfound"
                ));

        DiagnosticOrderTest orderTest = requireRadiologyTest(
                dto.orderTestId(),
                copy
        );

        ClaimEncounterCopyDiagnosticOrderTestReport entity =
                new ClaimEncounterCopyDiagnosticOrderTestReport();

        entity.setClaimEncounterCopyId(claimEncounterCopyId);
        entity.setOrderTestId(orderTest.getId());
        entity.setReport(dto.report());
        entity.setRadiologistInformation(dto.radiologistInformation());
        entity.setCriticalFindings(dto.criticalFindings());
        entity.setRadiologistComments(dto.radiologistComments());
        entity.setSeverity(dto.severity());
        entity.setStatus(PatientHistoryStatus.ACTIVE);

        return claimEncounterCopyDiagnosticOrderTestReportRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyDiagnosticOrderTestReport update(
            Long id,
            ClaimEncounterCopyDiagnosticOrderTestReportUpdateDTO dto
    ) {
        validate(
                dto.report(),
                dto.radiologistInformation(),
                dto.criticalFindings(),
                dto.radiologistComments()
        );

        ClaimEncounterCopyDiagnosticOrderTestReport entity =
                claimEncounterCopyDiagnosticOrderTestReportRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy diagnostic order test report not found with id "
                                        + id,
                                "claimEncounterCopyDiagnosticOrderTestReport",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled diagnostic order test report cannot be updated.",
                    "claimEncounterCopyDiagnosticOrderTestReport",
                    "cancelled"
            );
        }

        entity.setReport(dto.report());
        entity.setRadiologistInformation(dto.radiologistInformation());
        entity.setCriticalFindings(dto.criticalFindings());
        entity.setRadiologistComments(dto.radiologistComments());
        entity.setSeverity(dto.severity());

        return claimEncounterCopyDiagnosticOrderTestReportRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyDiagnosticOrderTestReport cancel(
            Long id,
            ClaimEncounterCopyDiagnosticOrderTestReportCancelDTO dto
    ) {
        ClaimEncounterCopyDiagnosticOrderTestReport entity =
                claimEncounterCopyDiagnosticOrderTestReportRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy diagnostic order test report not found with id "
                                        + id,
                                "claimEncounterCopyDiagnosticOrderTestReport",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Diagnostic order test report is already cancelled.",
                    "claimEncounterCopyDiagnosticOrderTestReport",
                    "already.cancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(
                dto.cancellationReason().trim()
        );

        return claimEncounterCopyDiagnosticOrderTestReportRepository
                .saveAndFlush(entity);
    }

    private DiagnosticOrderTest requireRadiologyTest(
            Long orderTestId,
            ClaimEncounterCopy copy
    ) {
        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository
                .findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "claimEncounterCopyDiagnosticOrderTestReport",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        if (orderTest.getOrderType() != TestType.RADIOLOGY) {
            throw new BadRequestAlertException(
                    "not_radiology_test",
                    "claimEncounterCopyDiagnosticOrderTestReport",
                    "DiagnosticOrderTest " + orderTestId + " is not a radiology test"
            );
        }

        DiagnosticOrder order = diagnosticOrderRepository
                .findById(orderTest.getOrderId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "claimEncounterCopyDiagnosticOrderTestReport",
                        "DiagnosticOrder not found with id " + orderTest.getOrderId()
                ));

        if (!order.getEncounterId().equals(copy.getEncounterId())) {
            throw new BadRequestAlertException(
                    "encounter_mismatch",
                    "claimEncounterCopyDiagnosticOrderTestReport",
                    "DiagnosticOrderTest " + orderTestId + " does not belong to this encounter"
            );
        }

        return orderTest;
    }

    private void validate(
            String report,
            String radiologistInformation,
            String criticalFindings,
            String radiologistComments
    ) {
        boolean hasContent =
                isNotBlank(report)
                        || isNotBlank(radiologistInformation)
                        || isNotBlank(criticalFindings)
                        || isNotBlank(radiologistComments);

        if (!hasContent) {
            throw new BadRequestAlertException(
                    "report_content_required",
                    "claimEncounterCopyDiagnosticOrderTestReport",
                    "At least one of report, radiologistInformation, criticalFindings or radiologistComments is required."
            );
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
