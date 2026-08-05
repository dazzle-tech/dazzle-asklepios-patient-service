package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class InsurancePatientShareCalculator {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePatientShareCalculator.class);

    private static final String ENTITY_NAME = "insurancePatientShare";

    private static final String SUCCESS_STATUS = "SUCCESS";

    private static final int MONEY_SCALE = 4;

    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;

    private final WaseelCoverageExtractionService waseelCoverageExtractionService;

    public InsuranceSplit calculateSplit(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        BigDecimal normalizedNet = money(netAmount);

        if (normalizedNet.signum() <= 0) {
            return new InsuranceSplit(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal patientShare =
                calculatePatientShare(
                        insurance,
                        serviceCategory,
                        serviceSource,
                        normalizedNet
                );

        BigDecimal insuranceShare =
                money(normalizedNet.subtract(patientShare));

        return new InsuranceSplit(patientShare, insuranceShare);
    }

    public BigDecimal calculatePatientShare(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource,
            BigDecimal netAmount
    ) {
        BigDecimal normalizedNet = money(netAmount);

        if (normalizedNet.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (isGpOrConsultationService(serviceCategory, serviceSource)) {
            BigDecimal gpVisitCopay = insurance.getGpVisitCopay();
            if (gpVisitCopay != null && gpVisitCopay.signum() >= 0) {
                LOG.debug(
                        "[INSURANCE] Using GP visit copay patientInsuranceId={} copay={} net={}",
                        insurance.getId(),
                        gpVisitCopay,
                        normalizedNet
                );
                return gpVisitCopay.min(normalizedNet);
            }
        }

        InsuranceCoverage coverage = loadCoverage(insurance);

        BigDecimal copaymentPercent =
                percentage(coverage.getCopaymentPercent());

        BigDecimal patientAmount =
                money(
                        normalizedNet
                                .multiply(copaymentPercent)
                                .divide(
                                        BigDecimal.valueOf(100),
                                        MONEY_SCALE,
                                        RoundingMode.HALF_UP
                                )
                );

        BigDecimal copaymentCap = money(coverage.getCopaymentCap());
        if (copaymentCap.signum() > 0) {
            patientAmount = patientAmount.min(copaymentCap);
        }

        return patientAmount.min(normalizedNet);
    }

    private InsuranceCoverage loadCoverage(PatientInsurance insurance) {
        WaseelEligibilityRequest eligibility =
                resolveEligibilityRequest(insurance);

        if (eligibility.getResponseJson() == null
                || eligibility.getResponseJson().isBlank()) {
            throw new BadRequestAlertException(
                    "Waseel eligibility response JSON is missing.",
                    ENTITY_NAME,
                    "insurance.eligibility.response.missing"
            );
        }

        try {
            return waseelCoverageExtractionService.extractCoverage(
                    eligibility.getResponseJson()
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "[INSURANCE] Unable to extract Waseel coverage patientInsuranceId={} eligibilityId={}",
                    insurance.getId(),
                    eligibility.getId(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to extract insurance coverage from Waseel eligibility response.",
                    ENTITY_NAME,
                    "insurance.coverage.extract.failed"
            );
        }
    }

    private WaseelEligibilityRequest resolveEligibilityRequest(
            PatientInsurance insurance
    ) {
        Long patientId = insurance.getPatientId();
        if (patientId == null && insurance.getPatient() != null) {
            patientId = insurance.getPatient().getId();
        }

        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required for insurance billing.",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        Long resolvedPatientId = patientId;

        if (insurance.getId() != null) {
            return waseelEligibilityRequestRepository
                    .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                            resolvedPatientId,
                            insurance.getId(),
                            SUCCESS_STATUS
                    )
                    .or(() ->
                            waseelEligibilityRequestRepository
                                    .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                            resolvedPatientId,
                                            SUCCESS_STATUS
                                    )
                    )
                    .orElseThrow(() ->
                            new BadRequestAlertException(
                                    "A successful Waseel eligibility response is required before insurance billing.",
                                    ENTITY_NAME,
                                    "insurance.eligibility.required"
                            )
                    );
        }

        return waseelEligibilityRequestRepository
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        resolvedPatientId,
                        SUCCESS_STATUS
                )
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "A successful Waseel eligibility response is required before insurance billing.",
                                ENTITY_NAME,
                                "insurance.eligibility.required"
                        )
                );
    }

    private boolean isGpOrConsultationService(
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (serviceSource == ServiceSource.CONSULTATION_PORTAL) {
            return true;
        }

        if (serviceCategory == null || serviceCategory.isBlank()) {
            return false;
        }

        String normalized = serviceCategory.trim().toLowerCase();
        return normalized.contains("consult")
                || normalized.contains("gp")
                || normalized.contains("general practice");
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
