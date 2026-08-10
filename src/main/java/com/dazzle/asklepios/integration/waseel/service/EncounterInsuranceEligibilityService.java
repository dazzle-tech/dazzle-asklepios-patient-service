package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;

import static org.hibernate.id.IdentifierGenerator.ENTITY_NAME;

@Service
@RequiredArgsConstructor
public class EncounterInsuranceEligibilityService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterInsuranceEligibilityService.class);

    private static final EnumSet<BillingResponsibilityStatus> EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(BillingResponsibilityStatus.CANCELLED);

    private final PatientPaymentsRepository patientPaymentsRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final WaseelEligibilityCheckService waseelEligibilityCheckService;

    @Transactional
    public EligibilityCheckResponse checkEncounterEligibility(Long encounterId) {
        PatientPayments payment = getInsurancePayment(encounterId);
        PatientInsurance insurance = payment.getPlan();

        validateInsuranceBeforeEligibility(insurance);

        EligibilityCheckRequest request = new EligibilityCheckRequest(
                payment.getPatient().getId(),
                insurance.getId(),
                payment.getEncounter().getId(),
                LocalDate.now(),
                resolveDestinationId(insurance),
                true,
                false,
                true,
                false,
                false
        );

        return waseelEligibilityCheckService.checkEligibility(request);
    }

    @Transactional(readOnly = true)
    public boolean isInsuranceEncounter(Long encounterId) {
        return shouldEvaluatePreAuthorization(encounterId);
    }

    /**
     * Pre-authorization is evaluated only for insurance visits.
     * Self-pay / cash visits must never be blocked by pre-authorization rules.
     */
    @Transactional(readOnly = true)
    public boolean shouldEvaluatePreAuthorization(Long encounterId) {
        if (encounterId == null) {
            return false;
        }

        BillingCoverageType encounterCoverageType = resolveEncounterCoverageType(encounterId);
        if (encounterCoverageType == BillingCoverageType.SELF_PAY) {
            LOG.info("[PREAUTH] Self-pay encounter coverage stored on encounter. encounterId={}", encounterId);
            return false;
        }
        if (encounterCoverageType == BillingCoverageType.INSURANCE) {
            LOG.info("[PREAUTH] Insurance encounter coverage stored on encounter. encounterId={}", encounterId);
            return true;
        }

        if (hasInsurancePayment(encounterId)) {
            LOG.info("[PREAUTH] Insurance visit detected via patient payment. encounterId={}", encounterId);
            return true;
        }

        if (patientServiceAndProductRepository.existsByEncounterIdAndPatientInsuranceIdIsNotNull(encounterId)) {
            LOG.info("[PREAUTH] Insurance visit detected via billed item insurance link. encounterId={}", encounterId);
            return true;
        }

        boolean hasInsuranceResponsibility =
                billingChargeResponsibilityRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        )
                        .stream()
                        .anyMatch(responsibility ->
                                responsibility.getResponsiblePartyType() == ResponsiblePartyType.INSURANCE);

        if (hasInsuranceResponsibility) {
            LOG.info("[PREAUTH] Insurance visit detected via billing responsibility. encounterId={}", encounterId);
            return true;
        }

        LOG.info("[PREAUTH] Visit treated as non-insurance for pre-authorization. encounterId={}", encounterId);
        return false;
    }

    @Transactional(readOnly = true)
    public Long resolveEncounterPatientInsuranceId(Long encounterId) {
        if (encounterId == null) {
            return null;
        }

        return patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getPatientInsuranceId)
                .filter(id -> id != null)
                .or(() ->
                        patientPaymentsRepository
                                .findFirstByEncounterIdOrderByIdDesc(encounterId)
                                .filter(payment ->
                                        PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes()))
                                .map(PatientPayments::getPlan)
                                .map(PatientInsurance::getId)
                )
                .or(() ->
                        patientServiceAndProductRepository
                                .findFirstByEncounterIdAndPatientInsuranceIdIsNotNullOrderByIdDesc(
                                        encounterId
                                )
                                .map(PatientServiceAndProduct::getPatientInsuranceId)
                )
                .orElse(null);
    }

    private BillingCoverageType resolveEncounterCoverageType(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getCoverageType)
                .orElse(null);
    }

    private boolean hasInsurancePayment(Long encounterId) {
        return patientPaymentsRepository
                .findFirstByEncounterIdOrderByIdDesc(encounterId)
                .map(payment ->
                        PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes())
                                && payment.getPlan() != null)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean shouldEvaluatePreAuthorization(
            Long encounterId,
            BillingCoverageType coverageType
    ) {
        if (shouldEvaluatePreAuthorization(encounterId)) {
            return true;
        }

        return encounterId != null
                && coverageType == BillingCoverageType.INSURANCE;
    }

    @Transactional(readOnly = true)
    public PatientInsurance getValidatedInsuranceForPreAuthorization(Long encounterId) {
        if (hasInsurancePayment(encounterId)) {
            PatientInsurance insurance = getInsurancePayment(encounterId).getPlan();
            validateInsuranceBeforeEligibility(insurance);
            return insurance;
        }

        Long patientInsuranceId = resolveEncounterPatientInsuranceId(encounterId);
        if (patientInsuranceId == null) {
            throw new BadRequestAlertException(
                    "Insurance is required for pre-authorization on this encounter.",
                    ENTITY_NAME,
                    "insurance.required"
            );
        }

        PatientInsurance insurance = patientInsuranceRepository.findById(patientInsuranceId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient insurance not found with id " + patientInsuranceId,
                        ENTITY_NAME,
                        "insurance.notFound"
                ));

        validateInsuranceBeforeEligibility(insurance);
        return insurance;
    }

    private PatientPayments getInsurancePayment(Long encounterId) {
        PatientPayments payment = patientPaymentsRepository
                .findFirstByEncounterIdOrderByIdDesc(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "No payment found for encounterId: " + encounterId,
                        ENTITY_NAME,
                        "payment.notFound"
                ));

        if (!PaymentTypes.INSURANCE_PLAN.equals(payment.getPaymentTypes())) {
            throw new BadRequestAlertException(
                    "Encounter payment type is not insurance.",
                    ENTITY_NAME,
                    "paymentType.insuranceRequired"
            );
        }

        if (payment.getPlan() == null) {
            throw new BadRequestAlertException(
                    "Insurance payment selected but no insurance plan is attached.",
                    ENTITY_NAME,
                    "insurance.required"
            );
        }

        return payment;
    }

    private void validateInsuranceBeforeEligibility(PatientInsurance insurance) {
        if (insurance == null) {
            throw new BadRequestAlertException(
                    "Insurance is required.",
                    ENTITY_NAME,
                    "insurance.required"
            );
        }

        if (insurance.getExpirationDate() == null ||
                insurance.getExpirationDate().isBefore(LocalDate.now())) {
            throw new BadRequestAlertException(
                    "Insurance plan is expired.",
                    ENTITY_NAME,
                    "insurance.expired"
            );
        }

        if (isBlank(insurance.getMemberCardId())) {
            throw new BadRequestAlertException(
                    "memberCardId is required for eligibility.",
                    ENTITY_NAME,
                    "memberCardId.required"
            );
        }

        if (isBlank(insurance.getPolicyNumber())) {
            throw new BadRequestAlertException(
                    "policyNumber is required for eligibility.",
                    ENTITY_NAME,
                    "policyNumber.required"
            );
        }
    }

    private String resolveDestinationId(PatientInsurance insurance) {
        if (insurance == null) {
            return null;
        }

        if (!isBlank(insurance.getTpaNphiesId())) {
            return insurance.getTpaNphiesId().trim();
        }

        if (!isBlank(insurance.getPayerNphiesId())) {
            return insurance.getPayerNphiesId().trim();
        }

        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}