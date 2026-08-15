package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PreAuthorizationResubmissionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationResubmissionService.class);

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationTrackingService trackingService;

    /**
     * Creates a new Waseel pre-authorization from a failed original request.
     * Linked billing items are reset to pending, re-priced on submit, and sent as a new request.
     */
    @Transactional
    public PreAuthorizationTrackingResponse resubmit(Long preAuthorizationId) {
        PreAuthorizationRequest original = preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        "preAuthorization",
                        "notfound"
                ));

        validateCanResubmit(original);

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findByPreAuthorizationRequestId(original.getId());

        if (items == null || items.isEmpty()) {
            throw new BadRequestAlertException(
                    "No billing items are linked to this pre-authorization to resubmit.",
                    "preAuthorization",
                    "resubmit.items.notFound"
            );
        }

        for (PatientServiceAndProduct item : items) {
            if (Boolean.TRUE.equals(item.getIsBilled())) {
                throw new BadRequestAlertException(
                        "Cannot resubmit because a linked item is already billed.",
                        "preAuthorization",
                        "resubmit.item.alreadyBilled"
                );
            }
            if (item.getPaymentStatus() == PaymentStatus.CANCELLED) {
                throw new BadRequestAlertException(
                        "Cannot resubmit because a linked item is cancelled.",
                        "preAuthorization",
                        "resubmit.item.cancelled"
                );
            }

            item.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
            item.setPreAuthorizationRequired(true);
            item.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
            item.setPreAuthorizationRequestId(null);
            item.setPreAuthorizationReferenceNo(null);
        }

        patientServiceAndProductRepository.saveAllAndFlush(items);

        LOG.info(
                "[PREAUTH_RESUBMIT] Cloning failed request into a new submission. "
                        + "originalPreAuthId={} encounterId={} itemCount={}",
                original.getId(),
                original.getEncounterId(),
                items.size()
        );

        encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(
                original.getEncounterId()
        );

        Long newRequestId = patientServiceAndProductRepository
                .findById(items.get(0).getId())
                .map(PatientServiceAndProduct::getPreAuthorizationRequestId)
                .orElse(null);

        if (newRequestId == null) {
            throw new BadRequestAlertException(
                    "Pre-authorization was reset but a new Waseel request was not created.",
                    "preAuthorization",
                    "resubmit.submit.failed"
            );
        }

        LOG.info(
                "[PREAUTH_RESUBMIT] New request created. originalPreAuthId={} newPreAuthId={}",
                original.getId(),
                newRequestId
        );

        return trackingService.findById(newRequestId);
    }

    static boolean canResubmit(PreAuthorizationRequest request) {
        if (request == null || Boolean.TRUE.equals(request.getIsCancelled())) {
            return false;
        }

        return isFailedSubmission(request.getStatus(), request.getOutcome());
    }

    private void validateCanResubmit(PreAuthorizationRequest request) {
        if (Boolean.TRUE.equals(request.getIsCancelled())) {
            throw new BadRequestAlertException(
                    "Cancelled pre-authorizations cannot be resubmitted.",
                    "preAuthorization",
                    "resubmit.cancelled"
            );
        }

        if (!canResubmit(request)) {
            throw new BadRequestAlertException(
                    "Only failed or rejected pre-authorizations can be resubmitted.",
                    "preAuthorization",
                    "resubmit.notEligible"
            );
        }
    }

    private static boolean isFailedSubmission(String status, String outcome) {
        return containsFailure(status) || containsFailure(outcome);
    }

    private static boolean containsFailure(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("error")
                || normalized.contains("failed")
                || normalized.contains("rejected")
                || normalized.contains("denied");
    }
}
