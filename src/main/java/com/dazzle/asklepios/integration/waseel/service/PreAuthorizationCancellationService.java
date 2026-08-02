package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthorizationCancellationService {

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final WaseelPreAuthorizationService waseelPreAuthorizationService;

    /**
     * Cancels Waseel pre-authorization linked to a patient service/product item (if any).
     * Waseel cancel is at approval-request level, so all items sharing that request are marked cancelled locally.
     */
    @Transactional
    public void cancelForItem(PatientServiceAndProduct item, CancelReason cancelReason) {
        if (item == null) {
            return;
        }

        PreAuthorizationStatus status = item.getPreAuthorizationStatus();
        if (!requiresPreAuthorizationCancel(status)) {
            return;
        }

        CancelReason reason = cancelReason == null
                ? CancelReason.SERVICE_NOT_PERFORMED
                : cancelReason;

        PreAuthorizationRequest preAuth = resolvePreAuthorization(item);

        if (preAuth == null) {
            log.info(
                    "[PREAUTH_CANCEL] No pre-auth request found for pspId={} — marking item cancelled locally",
                    item.getId()
            );
            markItemCancelled(item);
            return;
        }

        if (Boolean.TRUE.equals(preAuth.getIsCancelled())) {
            log.info(
                    "[PREAUTH_CANCEL] Pre-auth already cancelled locally. preAuthId={} pspId={}",
                    preAuth.getId(),
                    item.getId()
            );
            markLinkedItemsCancelled(preAuth, item);
            return;
        }

        if (preAuth.getApprovalRequestId() == null) {
            log.info(
                    "[PREAUTH_CANCEL] Pre-auth has no Waseel approvalRequestId — cancelling locally. preAuthId={} pspId={}",
                    preAuth.getId(),
                    item.getId()
            );
            preAuth.setIsCancelled(Boolean.TRUE);
            preAuth.setCancelReason(reason);
            preAuth.setStatus("CANCELLED");
            preAuthorizationRequestRepository.save(preAuth);
            markLinkedItemsCancelled(preAuth, item);
            return;
        }

        try {
            log.info(
                    "[PREAUTH_CANCEL] Cancelling Waseel pre-auth. preAuthId={} approvalRequestId={} pspId={}",
                    preAuth.getId(),
                    preAuth.getApprovalRequestId(),
                    item.getId()
            );

            waseelPreAuthorizationService.cancel(
                    new PreAuthorizationCancelRequest(
                            preAuth.getId(),
                            preAuth.getApprovalRequestId(),
                            reason
                    )
            );
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            String details = "Failed to cancel pre-authorization in Waseel (HTTP "
                    + ex.getStatusCode().value()
                    + ")"
                    + (body == null || body.isBlank() ? ": " + ex.getMessage() : ": " + body);

            throw new BadRequestAlertException(
                    details,
                    "preAuthorization",
                    "waseel.cancel.failed"
            );
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Failed to cancel pre-authorization in Waseel: " + ex.getMessage(),
                    "preAuthorization",
                    "waseel.cancel.failed"
            );
        }

        markLinkedItemsCancelled(preAuth, item);
    }

    private boolean requiresPreAuthorizationCancel(PreAuthorizationStatus status) {
        return status == PreAuthorizationStatus.PENDING_APPROVAL
                || status == PreAuthorizationStatus.APPROVED;
    }

    private PreAuthorizationRequest resolvePreAuthorization(PatientServiceAndProduct item) {
        if (item.getPreAuthorizationRequestId() == null) {
            return null;
        }

        return preAuthorizationRequestRepository
                .findById(item.getPreAuthorizationRequestId())
                .orElse(null);
    }

    private void markLinkedItemsCancelled(
            PreAuthorizationRequest preAuth,
            PatientServiceAndProduct triggeringItem
    ) {
        List<PatientServiceAndProduct> linkedItems =
                patientServiceAndProductRepository.findByPreAuthorizationRequestId(preAuth.getId());

        for (PatientServiceAndProduct linked : linkedItems) {
            markItemCancelled(linked, preAuth);
        }

        if (triggeringItem.getId() != null
                && linkedItems.stream().noneMatch(linked -> triggeringItem.getId().equals(linked.getId()))) {
            markItemCancelled(triggeringItem, preAuth);
        }
    }

    private void markItemCancelled(PatientServiceAndProduct item) {
        item.setPreAuthorizationStatus(PreAuthorizationStatus.CANCELLED);
        item.setPreAuthorizationRequired(false);
        patientServiceAndProductRepository.save(item);
    }

    private void markItemCancelled(PatientServiceAndProduct item, PreAuthorizationRequest preAuth) {
        item.setPreAuthorizationRequestId(preAuth.getId());
        item.setPreAuthorizationReferenceNo(preAuth.getPreAuthRefNo());
        markItemCancelled(item);
    }
}
