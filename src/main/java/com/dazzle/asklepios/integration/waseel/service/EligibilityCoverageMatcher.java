package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EligibilityCoverageMatcher {

    public EligibilityCoverageDTO findMatchingCoverage(
            PatientInsurance insurance,
            List<EligibilityCoverageDTO> coverages
    ) {
        if (insurance == null) {
            return findMatchingCoverage(coverages, null, null);
        }

        return findMatchingCoverage(
                coverages,
                insurance.getMemberCardId(),
                insurance.getPolicyNumber()
        );
    }

    public EligibilityCoverageDTO findMatchingCoverage(
            List<EligibilityCoverageDTO> coverages,
            String memberCardId,
            String policyNumber
    ) {
        if (coverages == null || coverages.isEmpty()) {
            return null;
        }

        String member = clean(memberCardId);
        String policy = clean(policyNumber);

        if (member != null && policy != null) {
            for (EligibilityCoverageDTO coverage : coverages) {
                if (member.equals(clean(coverage.memberId()))
                        && policyMatches(coverage, policy)) {
                    return coverage;
                }
            }
        }

        if (member != null) {
            for (EligibilityCoverageDTO coverage : coverages) {
                if (member.equals(clean(coverage.memberId()))) {
                    return coverage;
                }
            }
        }

        if (policy != null) {
            for (EligibilityCoverageDTO coverage : coverages) {
                if (policyMatches(coverage, policy)) {
                    return coverage;
                }
            }
        }

        return null;
    }

    private boolean policyMatches(EligibilityCoverageDTO coverage, String policy) {
        if (policy.equals(clean(coverage.policyNumber()))) {
            return true;
        }

        if (coverage.classList() == null) {
            return false;
        }

        for (EligibilityClassDTO classItem : coverage.classList()) {
            if (classItem == null) {
                continue;
            }

            if (policy.equals(clean(classItem.classValue()))) {
                return true;
            }
        }

        return false;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
