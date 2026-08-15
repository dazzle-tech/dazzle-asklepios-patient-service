package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.EncounterCoverageDTO;
import com.dazzle.asklepios.service.dto.billing.UpdateEncounterCoverageRequest;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterCoverageService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterCoverageService.class);

    private static final String ENTITY_NAME = "encounterCoverage";

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientPaymentsRepository patientPaymentsRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;

    public EncounterCoverageService(
            PatientEncounterRepository patientEncounterRepository,
            PatientPaymentsRepository patientPaymentsRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            @Lazy EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientPaymentsRepository = patientPaymentsRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
    }

    @Transactional(readOnly = true)
    public EncounterCoverageDTO getEncounterCoverage(Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);

        PatientPayments payment =
                patientPaymentsRepository
                        .findFirstByEncounterIdOrderByIdDesc(encounterId)
                        .orElse(null);

        BillingCoverageType coverageType = resolveCoverageType(encounter, payment);
        Long patientInsuranceId = resolvePatientInsuranceId(encounter, payment);
        PaymentTypes paymentTypes = payment == null ? null : payment.getPaymentTypes();
        boolean insuranceVisit = coverageType == BillingCoverageType.INSURANCE;

        boolean hasPendingPreAuthorization =
                patientServiceAndProductRepository.existsByEncounterIdAndPreAuthorizationStatus(
                        encounterId,
                        PreAuthorizationStatus.PENDING_APPROVAL
                );

        return new EncounterCoverageDTO(
                encounterId,
                coverageType,
                patientInsuranceId,
                paymentTypes,
                insuranceVisit,
                hasPendingPreAuthorization
        );
    }

    @Transactional
    public EncounterCoverageDTO setEncounterCoverage(
            Long encounterId,
            UpdateEncounterCoverageRequest request
    ) {
        PatientEncounter encounter = getEncounter(encounterId);
        validateCoverageRequest(encounter, request);

        encounter.setCoverageType(request.coverageType());
        encounter.setPatientInsuranceId(
                request.coverageType() == BillingCoverageType.INSURANCE
                        ? request.patientInsuranceId()
                        : null
        );

        patientEncounterRepository.saveAndFlush(encounter);

        LOG.info(
                "[ENCOUNTER_COVERAGE] Saved encounterId={} coverageType={} patientInsuranceId={}",
                encounterId,
                request.coverageType(),
                encounter.getPatientInsuranceId()
        );

        if (request.coverageType() == BillingCoverageType.INSURANCE) {
            encounterPreAuthorizationSyncService.scheduleSyncAfterCommit(
                    encounterId,
                    BillingCoverageType.INSURANCE
            );
        }

        return getEncounterCoverage(encounterId);
    }

    @Transactional
    public void applyCoverageFromPayment(PatientEncounter encounter, PatientPayments payment) {
        if (encounter == null || payment == null || payment.getPaymentTypes() == null) {
            return;
        }

        BillingCoverageType coverageType =
                PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes())
                        ? BillingCoverageType.INSURANCE
                        : BillingCoverageType.SELF_PAY;

        Long patientInsuranceId =
                coverageType == BillingCoverageType.INSURANCE && payment.getPlan() != null
                        ? payment.getPlan().getId()
                        : null;

        applyCoverage(encounter, coverageType, patientInsuranceId);
    }

    @Transactional
    public void applyCoverage(
            PatientEncounter encounter,
            BillingCoverageType coverageType,
            Long patientInsuranceId
    ) {
        if (encounter == null || coverageType == null) {
            return;
        }

        UpdateEncounterCoverageRequest request =
                new UpdateEncounterCoverageRequest(coverageType, patientInsuranceId);

        validateCoverageRequest(encounter, request);

        encounter.setCoverageType(coverageType);
        encounter.setPatientInsuranceId(
                coverageType == BillingCoverageType.INSURANCE
                        ? patientInsuranceId
                        : null
        );

        patientEncounterRepository.saveAndFlush(encounter);

        LOG.info(
                "[ENCOUNTER_COVERAGE] Applied encounterId={} coverageType={} patientInsuranceId={}",
                encounter.getId(),
                coverageType,
                encounter.getPatientInsuranceId()
        );
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));
    }

    private BillingCoverageType resolveCoverageType(
            PatientEncounter encounter,
            PatientPayments payment
    ) {
        if (encounter.getCoverageType() != null) {
            return encounter.getCoverageType();
        }

        if (payment != null) {
            if (PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes())) {
                return BillingCoverageType.INSURANCE;
            }
            if (PaymentTypes.CASH.equals(payment.getPaymentTypes())) {
                return BillingCoverageType.SELF_PAY;
            }
        }

        if (encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounter.getId())) {
            return BillingCoverageType.INSURANCE;
        }

        return BillingCoverageType.SELF_PAY;
    }

    private Long resolvePatientInsuranceId(
            PatientEncounter encounter,
            PatientPayments payment
    ) {
        if (encounter.getPatientInsuranceId() != null) {
            return encounter.getPatientInsuranceId();
        }

        if (payment != null
                && PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes())
                && payment.getPlan() != null) {
            return payment.getPlan().getId();
        }

        return encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(encounter.getId());
    }

    private void validateCoverageRequest(
            PatientEncounter encounter,
            UpdateEncounterCoverageRequest request
    ) {
        if (request.coverageType() == BillingCoverageType.INSURANCE) {
            if (request.patientInsuranceId() == null) {
                throw new BadRequestAlertException(
                        "patientInsuranceId is required when coverageType is INSURANCE",
                        ENTITY_NAME,
                        "patientInsuranceId.required"
                );
            }

            PatientInsurance insurance =
                    patientInsuranceRepository.findByIdAndPatient_Id(
                                    request.patientInsuranceId(),
                                    encounter.getPatient().getId()
                            )
                            .orElseThrow(() -> new NotFoundAlertException(
                                    "Patient insurance not found with id "
                                            + request.patientInsuranceId(),
                                    ENTITY_NAME,
                                    "patientInsurance.notfound"
                            ));
        } else if (request.patientInsuranceId() != null) {
            throw new BadRequestAlertException(
                    "patientInsuranceId must be null when coverageType is SELF_PAY",
                    ENTITY_NAME,
                    "patientInsuranceId.notAllowed"
            );
        }
    }
}
