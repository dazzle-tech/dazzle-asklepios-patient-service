package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingEligibilitySnapshot;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.BillingEligibilitySnapshotRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
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
    private final ObjectMapper objectMapper;

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
    public Optional<String> resolveEligibilityReference(Long encounterId) {
        return snapshotRepository.findByEncounterId(encounterId)
                .map(BillingEligibilitySnapshot::getEligibilityResponseId);
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

        WaseelCoverageDetails coverage =
                coverageExtractionService.extractCoverageDetails(
                        eligibility.getResponseJson()
                );

        String frozenBy =
                SecurityUtils.getCurrentUserLogin()
                        .orElse("system");

        Instant frozenAt = Instant.now();

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
                .memberId(coverage.memberId())
                .policyNumber(coverage.policyNumber())
                .policyHolder(coverage.policyHolder())
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
