package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientPayments;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.repository.PatientPaymentsRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hibernate.id.IdentifierGenerator.ENTITY_NAME;

@Service
@RequiredArgsConstructor
public class EncounterInsuranceEligibilityService {

    private final PatientPaymentsRepository patientPaymentsRepository;
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
                null,
                true,
                false,
                true,
                false,
                false
        );

        return waseelEligibilityCheckService.checkEligibility(request);
    }

    @Transactional(readOnly = true)
    public PatientInsurance getValidatedInsuranceForPreAuthorization(Long encounterId) {
        PatientPayments payment = getInsurancePayment(encounterId);
        PatientInsurance insurance = payment.getPlan();

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

        if (isBlank(insurance.getPayerNphiesId())) {
            throw new BadRequestAlertException(
                    "payerNphiesId is required for eligibility.",
                    ENTITY_NAME,
                    "payerNphiesId.required"
            );
        }

        if (insurance.getPayorId() == null) {
            throw new BadRequestAlertException(
                    "payorId is required for eligibility.",
                    ENTITY_NAME,
                    "payorId.required"
            );
        }

        if (insurance.getPlanId() == null) {
            throw new BadRequestAlertException(
                    "planId is required for eligibility.",
                    ENTITY_NAME,
                    "planId.required"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}