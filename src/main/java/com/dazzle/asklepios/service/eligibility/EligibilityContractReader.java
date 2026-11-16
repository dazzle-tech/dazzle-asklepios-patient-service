package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.service.InsuranceBenefitRuleService;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Reads the patient-level eligibility contract: the benefit rules stored from the latest eligibility
 * check of the insurance (patient_insurance_benefit_rules keeps only the latest check). The in-force
 * state comes from the fields the same check wrote on the insurance, so no eligibility response is read.
 */
@Service
@RequiredArgsConstructor
public class EligibilityContractReader {

    private static final ZoneId PAYER_ZONE = ZoneId.of("Asia/Riyadh");

    private final InsuranceBenefitRuleService insuranceBenefitRuleService;

    /** The contract rules, or an empty list when the insurance has no contract in force. */
    public List<InsuranceBenefitRule> activeRules(PatientInsurance insurance) {
        if (insurance == null || insurance.getId() == null || !isInForce(insurance, LocalDate.now(PAYER_ZONE))) {
            return List.of();
        }
        return insuranceBenefitRuleService.getStoredRules(insurance.getId());
    }

    /** In force as of the latest check, and the benefit period and the policy have not ended. */
    static boolean isInForce(PatientInsurance insurance, LocalDate today) {
        String inforce = insurance.getInforce() == null ? "" : insurance.getInforce().trim();
        return "true".equalsIgnoreCase(inforce)
                && !endsBefore(insurance.getBenefitEndDate(), today)
                && !endsBefore(insurance.getExpirationDate(), today);
    }

    private static boolean endsBefore(LocalDate endDate, LocalDate today) {
        return endDate != null && endDate.isBefore(today);
    }
}
