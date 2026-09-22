package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingEligibilitySnapshot;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.service.EligibilityRequestPlanIdentityReader;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.BillingEligibilitySnapshotRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingEligibilitySnapshotResponse;
import com.dazzle.asklepios.service.dto.billing.FreezeEligibilitySnapshotRequest;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingEligibilitySnapshotService {

    private static final String ENTITY_NAME = "billingEligibilitySnapshot";
    private static final String SUCCESS_STATUS = "SUCCESS";

    private final BillingEligibilitySnapshotRepository snapshotRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;
    private final WaseelCoverageExtractionService coverageExtractionService;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final EligibilityRequestPlanIdentityReader requestPlanIdentityReader;
    private final ObjectMapper objectMapper;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;

    @Transactional(readOnly = true)
    public Optional<BillingEligibilitySnapshotResponse> findByEncounterId(Long encounterId) {
        return snapshotRepository.findByEncounterId(encounterId)
                .map(this::mapResponse);
    }

    @Transactional
    public BillingEligibilitySnapshotResponse freezeForEncounter(
            Long encounterId,
            FreezeEligibilitySnapshotRequest request
    ) {
        if (snapshotRepository.existsByEncounterId(encounterId)) {
            return snapshotRepository.findByEncounterId(encounterId)
                    .map(this::mapResponse)
                    .orElseThrow();
        }

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + encounterId,
                                ENTITY_NAME,
                                "encounter.notfound"
                        )
                );

        if (!isEligibilityFreezeRequired(
                encounter,
                request == null ? null : request.patientInsuranceId()
        )) {
            throw new BadRequestAlertException(
                    "Eligibility freeze is not required for this insurance coverage.",
                    ENTITY_NAME,
                    "eligibility.freeze.notRequired"
            );
        }

        if (encounter.getPatient() == null || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is missing.",
                    ENTITY_NAME,
                    "encounter.patient.missing"
            );
        }

        WaseelEligibilityRequest eligibility =
                resolveEligibilityRequest(
                        encounter,
                        request == null ? null : request.patientInsuranceId()
                );

        BillingEligibilitySnapshot snapshot =
                buildSnapshot(encounter, eligibility);

        return mapResponse(snapshotRepository.save(snapshot));
    }

    @Transactional
    public BillingEligibilitySnapshotResponse ensureFrozenForEncounter(Long encounterId) {
        return snapshotRepository.findByEncounterId(encounterId)
                .map(this::mapResponse)
                .orElseGet(() ->
                        freezeForEncounter(
                                encounterId,
                                new FreezeEligibilitySnapshotRequest(
                                        "auto-freeze-" + encounterId,
                                        null
                                )
                        )
                );
    }

    @Transactional(readOnly = true)
    public boolean isEligibilityFreezeRequired(Long encounterId) {
        if (encounterId == null) {
            return false;
        }

        return patientEncounterRepository.findById(encounterId)
                .map(encounter -> isEligibilityFreezeRequired(encounter, encounter.getPatientInsuranceId()))
                .orElse(false);
    }

    /**
     * Waseel visits still require a frozen eligibility snapshot.
     * Other payors use the selected patient-insurance expiration date.
     */
    @Transactional
    public void requireReadyForFinancialClose(Long encounterId) {
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + encounterId,
                                ENTITY_NAME,
                                "encounter.notfound"
                        )
                );

        PatientInsurance insurance = resolveInsurance(encounter, encounter.getPatientInsuranceId());
        if (insurance != null
                && !insurancePatientShareCalculator.isWaseelCoverage(insurance)) {
            if (!insurancePatientShareCalculator.isLatestCoverageInForce(insurance)) {
                throw new BadRequestAlertException(
                        "The patient's insurance coverage is not in-force.",
                        ENTITY_NAME,
                        "patientInsurance.expired"
                );
            }
            return;
        }

        try {
            ensureFrozenForEncounter(encounterId);
        } catch (NotFoundAlertException exception) {
            throw new BadRequestAlertException(
                    "Insurance eligibility is required before financial closure.",
                    ENTITY_NAME,
                    "encounter.eligibility.required"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveEligibilityReference(Long encounterId) {
        return snapshotRepository.findByEncounterId(encounterId)
                .map(BillingEligibilitySnapshot::getEligibilityResponseId);
    }

    private boolean isEligibilityFreezeRequired(
            PatientEncounter encounter,
            Long patientInsuranceId
    ) {
        return isEligibilityFreezeRequired(
                encounter,
                resolveInsurance(encounter, patientInsuranceId)
        );
    }

    private boolean isEligibilityFreezeRequired(
            PatientEncounter encounter,
            PatientInsurance insurance
    ) {
        if (insurance == null) {
            return true;
        }

        return insurancePatientShareCalculator.isWaseelCoverage(insurance);
    }

    private PatientInsurance resolveInsurance(
            PatientEncounter encounter,
            Long patientInsuranceId
    ) {
        Long insuranceId = patientInsuranceId;
        if (insuranceId == null && encounter != null) {
            insuranceId = encounter.getPatientInsuranceId();
        }
        if (insuranceId == null) {
            return null;
        }

        return patientInsuranceRepository.findById(insuranceId).orElse(null);
    }

    private WaseelEligibilityRequest resolveEligibilityRequest(
            PatientEncounter encounter,
            Long patientInsuranceId
    ) {
        Long patientId = encounter.getPatient().getId();
        Long encounterId = encounter.getId();

        if (patientInsuranceId != null) {
            return waseelEligibilityRequestRepository
                    .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                            patientId,
                            patientInsuranceId,
                            SUCCESS_STATUS
                    )
                    .or(() ->
                            waseelEligibilityRequestRepository
                                    .findFirstByEncounterIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByRespondedAtDesc(
                                            encounterId,
                                            SUCCESS_STATUS
                                    )
                    )
                    .orElseThrow(() ->
                            eligibilityNotFound()
                    );
        }

        return waseelEligibilityRequestRepository
                .findFirstByEncounterIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByRespondedAtDesc(
                        encounterId,
                        SUCCESS_STATUS
                )
                .or(() ->
                        waseelEligibilityRequestRepository
                                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                        patientId,
                                        SUCCESS_STATUS
                                )
                )
                .or(() ->
                        waseelEligibilityRequestRepository
                                .findTopByPatientIdAndRequestStatusOrderByRespondedAtDesc(
                                        patientId,
                                        SUCCESS_STATUS
                                )
                )
                .orElseThrow(this::eligibilityNotFound);
    }

    private BillingEligibilitySnapshot buildSnapshot(
            PatientEncounter encounter,
            WaseelEligibilityRequest eligibility
    ) {
        if (eligibility.getResponseJson() == null
                || eligibility.getResponseJson().isBlank()) {
            throw new BadRequestAlertException(
                    "Waseel eligibility response JSON is missing.",
                    ENTITY_NAME,
                    "eligibility.response.missing"
            );
        }

        PatientInsurance insurance =
                eligibility.getPatientInsuranceId() == null
                        ? null
                        : patientInsuranceRepository
                                .findById(eligibility.getPatientInsuranceId())
                                .orElse(null);

        WaseelCoverageDetails coverage =
                coverageExtractionService.extractCoverageDetails(
                        eligibility.getResponseJson(),
                        insurance == null ? null : insurance.getMemberCardId(),
                        insurance == null ? null : insurance.getPolicyNumber()
                );

        String frozenBy =
                SecurityUtils.getCurrentUserLogin()
                        .orElse("system");

        Instant frozenAt = Instant.now();

        EligibilityRequestPlanIdentityReader.PlanIdentity requestPlan =
                requestPlanIdentityReader.read(
                        eligibility.getRequestJson(),
                        insurance == null ? null : insurance.getMemberCardId(),
                        insurance == null ? null : insurance.getPolicyNumber()
                );

        return BillingEligibilitySnapshot.builder()
                .encounterId(encounter.getId())
                .patientId(encounter.getPatient().getId())
                .patientInsuranceId(eligibility.getPatientInsuranceId())
                .waseelEligibilityRequestId(eligibility.getId())
                .eligibilityResponseId(
                        firstNonBlank(
                                eligibility.getEligibilityResponseId(),
                                coverage.eligibilityResponseId()
                        )
                )
                .memberId(firstNonBlank(
                        requestPlan.memberCardId(),
                        insurance == null ? null : insurance.getMemberCardId(),
                        coverage.memberId()
                ))
                .policyNumber(firstNonBlank(
                        requestPlan.policyNumber(),
                        insurance == null ? null : insurance.getPolicyNumber(),
                        coverage.policyNumber()
                ))
                .policyHolder(firstNonBlank(
                        requestPlan.policyHolder(),
                        insurance == null ? null : insurance.getPolicyHolderName(),
                        coverage.policyHolder()
                ))
                .network(coverage.network())
                .coverageStatus(coverage.coverageStatus())
                .inforce(coverage.inforce())
                .copaymentPercent(coverage.copaymentPercent())
                .copaymentCap(coverage.copaymentCap())
                .coverageJson(writeJson(coverage))
                .responseJson(eligibility.getResponseJson())
                .frozenAt(frozenAt)
                .frozenBy(frozenBy)
                .build();
    }

    private BillingEligibilitySnapshotResponse mapResponse(
            BillingEligibilitySnapshot snapshot
    ) {
        return new BillingEligibilitySnapshotResponse(
                snapshot.getId(),
                snapshot.getEncounterId(),
                snapshot.getPatientId(),
                snapshot.getPatientInsuranceId(),
                snapshot.getWaseelEligibilityRequestId(),
                snapshot.getEligibilityResponseId(),
                snapshot.getMemberId(),
                snapshot.getPolicyNumber(),
                snapshot.getPolicyHolder(),
                snapshot.getNetwork(),
                snapshot.getCoverageStatus(),
                snapshot.getInforce(),
                snapshot.getCopaymentPercent(),
                snapshot.getCopaymentCap(),
                snapshot.getFrozenAt(),
                snapshot.getFrozenBy(),
                true
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestAlertException(
                    "Unable to serialize eligibility coverage snapshot.",
                    ENTITY_NAME,
                    "eligibility.snapshot.serialize.failed"
            );
        }
    }

    private NotFoundAlertException eligibilityNotFound() {
        return new NotFoundAlertException(
                "No successful Waseel eligibility response was found for this encounter.",
                ENTITY_NAME,
                "eligibility.notfound"
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }
}
