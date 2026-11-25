package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * A Waseel insurance visit with no laboratory or radiology investigation must say why none was done.
 * The claim sends that reason with the "investigation not performed" (INP) code, so the payer
 * does not reject the claim for missing investigations.
 */
@Service
@RequiredArgsConstructor
public class EncounterInvestigationReasonGuard {

    private static final String ENTITY_NAME = "invoiceGeneration";

    private final BillingEligibilitySnapshotService billingEligibilitySnapshotService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    /** True when a Waseel insurance visit without investigations has no reason yet. */
    public boolean isReasonMissing(PatientEncounter encounter) {
        return isReasonMissing(encounter, patientServiceAndProductRepository.findByEncounterId(encounter.getId()));
    }

    /**
     * Stores the given reason on the encounter, then fails when a Waseel insurance visit
     * without investigations still has no reason.
     */
    public void requireReasonWhenNoInvestigation(PatientEncounter encounter, String reason) {
        requireReasonWhenNoInvestigation(
                encounter,
                patientServiceAndProductRepository.findByEncounterId(encounter.getId()),
                reason
        );
    }

    /**
     * Stores the given reason on the encounter, then fails when a Waseel insurance visit
     * without investigations still has no reason.
     */
    public void requireReasonWhenNoInvestigation(
            PatientEncounter encounter,
            List<PatientServiceAndProduct> encounterServices,
            String reason
    ) {
        if (reason != null && !reason.isBlank()) {
            encounter.setInvestigationNotPerformedReason(reason.trim());
        }

        if (isReasonMissing(encounter, encounterServices)) {
            throw new BadRequestAlertException(
                    "Visit has no laboratory or radiology investigation. Enter why no investigation was performed.",
                    ENTITY_NAME,
                    "encounter.investigationNotPerformedReason.required"
            );
        }
    }

    private boolean isReasonMissing(PatientEncounter encounter, List<PatientServiceAndProduct> encounterServices) {
        String reason = encounter.getInvestigationNotPerformedReason();
        return (reason == null || reason.isBlank())
                && BillingCoverageType.INSURANCE.equals(encounter.getCoverageType())
                && billingEligibilitySnapshotService.isEligibilityFreezeRequired(encounter.getId())
                && !hasInvestigation(encounter.getId(), encounterServices);
    }

    private boolean hasInvestigation(Long encounterId, List<PatientServiceAndProduct> encounterServices) {
        boolean billedInvestigation = encounterServices.stream()
                .filter(service -> service.getPaymentStatus() != PaymentStatus.CANCELLED)
                .anyMatch(service -> service.getDiagnosticTestId() != null);
        if (billedInvestigation) {
            return true;
        }

        List<Long> orderIds = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED)
                .stream()
                .map(DiagnosticOrder::getId)
                .filter(Objects::nonNull)
                .toList();

        return !orderIds.isEmpty()
                && diagnosticOrderTestRepository.existsByOrderIdInAndStatusNot(
                        orderIds,
                        DiagnosticOrderTestStatus.CANCELLED
                );
    }
}
