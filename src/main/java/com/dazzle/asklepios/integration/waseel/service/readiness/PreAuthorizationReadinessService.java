package com.dazzle.asklepios.integration.waseel.service.readiness;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSupportingInfoMapper;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Checks that a visit has every field Waseel makes mandatory for its claim type before a
 * pre-authorization is sent. The supporting info is read with the same mapper that builds the
 * request, so the check matches what would be sent; no default value fills a missing field.
 */
@Service
@RequiredArgsConstructor
public class PreAuthorizationReadinessService {

    private static final Logger LOG = LoggerFactory.getLogger(PreAuthorizationReadinessService.class);
    private static final String ENTITY_NAME = "preAuthorization";

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ApprovalSupportingInfoMapper supportingInfoMapper;

    /** What the visit still needs before its waiting lines can be sent to Waseel. */
    @Transactional(readOnly = true)
    public PreAuthorizationReadiness check(Long encounterId) {
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException("Encounter not found", ENTITY_NAME, "encounter.notFound"));
        return check(encounter, waitingForWaseel(encounterId));
    }

    public PreAuthorizationReadiness check(PatientEncounter encounter, List<PatientServiceAndProduct> waitingItems) {
        PreAuthorizationClaimType claimType =
                PreAuthorizationReadiness.claimTypeOf(encounter.getEncounterType(), waitingItems);
        return PreAuthorizationReadiness.of(
                encounter.getId(),
                claimType,
                !patientDiagnosisRepository.findByEncounterId(encounter.getId()).isEmpty(),
                supportingInfoMapper.toSupportingInfo(encounter),
                waitingItems.size()
        );
    }

    /**
     * Stops the submission when a mandatory field is missing, with the list of fields to fill.
     * The lines stay held off the patient account until the user fills them and sends again.
     */
    public void requireReady(PatientEncounter encounter, List<PatientServiceAndProduct> waitingItems) {
        PreAuthorizationReadiness readiness = check(encounter, waitingItems);
        if (readiness.ready()) {
            return;
        }
        LOG.info(
                "[PREAUTH_READINESS] Pre-authorization not sent, fields missing. encounterId={} claimType={} missing={}",
                encounter.getId(),
                readiness.claimType(),
                readiness.missingFields().stream().map(PreAuthorizationReadiness.MissingField::code).toList()
        );
        throw readiness.toMissingFieldsException(ENTITY_NAME);
    }

    /** Lines held for a pre-authorization that has not been sent yet. */
    private List<PatientServiceAndProduct> waitingForWaseel(Long encounterId) {
        return patientServiceAndProductRepository
                .findByEncounterIdAndPreAuthorizationStatusAndPreAuthorizationRequestIdIsNull(
                        encounterId,
                        PreAuthorizationStatus.PENDING_APPROVAL
                )
                .stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsBilled()))
                .filter(item -> item.getPaymentStatus() != PaymentStatus.CANCELLED)
                .toList();
    }
}
