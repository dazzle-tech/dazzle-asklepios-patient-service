package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.PatientItemPricingService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.errors.PreAuthorizationSubmissionFailedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rebuilds and resends a pre-authorization that never reached a payer decision
 * (gateway / validation failure). Does not clone clinical orders.
 */
@Service
@RequiredArgsConstructor
public class PreAuthorizationResubmissionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationResubmissionService.class);

    static final String STATUS_FAILED = "FAILED";
    static final String STATUS_ERROR = "ERROR";
    static final String STATUS_SUPERSEDED = "SUPERSEDED";
    static final String STATUS_RESUBMITTING = "RESUBMITTING";

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientItemPricingService patientItemPricingService;
    private final PreAuthorizationSubmissionService preAuthorizationSubmissionService;
    private final PreAuthorizationTrackingService preAuthorizationTrackingService;

    @Transactional(noRollbackFor = PreAuthorizationSubmissionFailedException.class)
    public PreAuthorizationTrackingResponse resubmit(Long preAuthorizationId) {
        PreAuthorizationRequest failedRequest = preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        "preAuthorization",
                        "notfound"
                ));

        if (!canResubmit(failedRequest)) {
            throw new BadRequestAlertException(
                    "This pre-authorization cannot be resubmitted. "
                            + "Only failed submissions that were not accepted by the payer can be retried.",
                    "preAuthorization",
                    "preAuthorization.resubmit.notAllowed"
            );
        }

        Long encounterId = failedRequest.getEncounterId();
        Long facilityId = patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getFacilityId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter facility is required to resubmit pre-authorization.",
                        "preAuthorization",
                        "encounter.facility.required"
                ));

        List<PatientServiceAndProduct> items = collectItemsForResubmit(failedRequest);
        if (items.isEmpty()) {
            throw new BadRequestAlertException(
                    "No pending items remain to resubmit for this pre-authorization.",
                    "preAuthorization",
                    "preAuthorization.resubmit.noItems"
            );
        }

        for (PatientServiceAndProduct item : items) {
            patientItemPricingService.applyResolvedPricing(item, facilityId);
            item.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
            item.setPreAuthorizationRequired(true);
            item.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
            item.setPreAuthorizationRequestId(null);
            item.setPreAuthorizationReferenceNo(null);
        }
        patientServiceAndProductRepository.saveAll(items);
        patientServiceAndProductRepository.flush();

        failedRequest.setStatus(STATUS_RESUBMITTING);
        preAuthorizationRequestRepository.saveAndFlush(failedRequest);

        LOG.info(
                "[PREAUTH_RESUBMIT] Retrying failed pre-authorization. failedRequestId={} encounterId={} itemCount={}",
                failedRequest.getId(),
                encounterId,
                items.size()
        );

        try {
            preAuthorizationSubmissionService.submitIfRequiredJoiningTransaction(encounterId);
            markSuperseded(failedRequest, "Superseded by a successful resubmission.");
            return latestRequestResponse(encounterId, failedRequest.getId());
        } catch (PreAuthorizationSubmissionFailedException ex) {
            markSuperseded(failedRequest, "Superseded by a later resubmission attempt.");
            throw ex;
        } catch (RuntimeException ex) {
            failedRequest.setStatus(STATUS_FAILED);
            preAuthorizationRequestRepository.saveAndFlush(failedRequest);
            throw ex;
        }
    }

    static boolean canResubmit(PreAuthorizationRequest request) {
        if (request == null || Boolean.TRUE.equals(request.getIsCancelled())) {
            return false;
        }

        String status = request.getStatus();
        if (status == null) {
            return false;
        }

        boolean failedSubmission =
                STATUS_FAILED.equalsIgnoreCase(status.trim())
                        || STATUS_ERROR.equalsIgnoreCase(status.trim());

        boolean neverAcceptedByPayer =
                request.getApprovalRequestId() == null
                        && request.getApprovalResponseId() == null
                        && (request.getPreAuthRefNo() == null || request.getPreAuthRefNo().isBlank());

        return failedSubmission && neverAcceptedByPayer;
    }

    private List<PatientServiceAndProduct> collectItemsForResubmit(PreAuthorizationRequest failedRequest) {
        Map<Long, PatientServiceAndProduct> byId = new LinkedHashMap<>();

        patientServiceAndProductRepository
                .findByPreAuthorizationRequestId(failedRequest.getId())
                .forEach(item -> addIfResubmittable(byId, item));

        List<PreAuthorizationItem> snapshotItems =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(failedRequest.getId());

        List<PatientServiceAndProduct> encounterItems =
                patientServiceAndProductRepository.findByEncounterId(failedRequest.getEncounterId());

        for (PreAuthorizationItem snapshot : snapshotItems) {
            encounterItems.stream()
                    .filter(item -> matchesSnapshot(item, snapshot))
                    .findFirst()
                    .ifPresent(item -> addIfResubmittable(byId, item));
        }

        encounterItems.stream()
                .filter(item -> item.getPreAuthorizationRequestId() == null)
                .filter(item -> item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL)
                .forEach(item -> addIfResubmittable(byId, item));

        return List.copyOf(byId.values());
    }

    private void addIfResubmittable(
            Map<Long, PatientServiceAndProduct> byId,
            PatientServiceAndProduct item
    ) {
        if (item == null || item.getId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(item.getIsBilled())) {
            return;
        }
        if (item.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return;
        }
        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.APPROVED
                || item.getPreAuthorizationStatus() == PreAuthorizationStatus.NOT_REQUIRED) {
            return;
        }
        byId.putIfAbsent(item.getId(), item);
    }

    private boolean matchesSnapshot(PatientServiceAndProduct item, PreAuthorizationItem snapshot) {
        if (item == null || snapshot == null) {
            return false;
        }
        return Objects.equals(item.getProcedureId(), snapshot.getProcedureId())
                && Objects.equals(item.getServiceId(), snapshot.getServiceId())
                && Objects.equals(item.getDiagnosticTestId(), snapshot.getDiagnosticTestId())
                && Objects.equals(item.getBrandMedicationId(), snapshot.getBrandMedicationId());
    }

    private void markSuperseded(PreAuthorizationRequest failedRequest, String message) {
        failedRequest.setStatus(STATUS_SUPERSEDED);
        failedRequest.setMessage(message);
        preAuthorizationRequestRepository.saveAndFlush(failedRequest);
    }

    private PreAuthorizationTrackingResponse latestRequestResponse(Long encounterId, Long excludedId) {
        return preAuthorizationRequestRepository.findByEncounterIdOrderByIdDesc(encounterId)
                .stream()
                .filter(request -> !Objects.equals(request.getId(), excludedId))
                .findFirst()
                .map(request -> preAuthorizationTrackingService.findById(request.getId()))
                .orElseGet(() -> preAuthorizationTrackingService.findById(excludedId));
    }
}
