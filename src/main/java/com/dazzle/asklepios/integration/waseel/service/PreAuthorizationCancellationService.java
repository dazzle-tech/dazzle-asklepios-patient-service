package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
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
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthorizationCancellationService {

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final WaseelPreAuthorizationService waseelPreAuthorizationService;

    /**
     * Cancels Waseel pre-authorization linked to a patient service/product item (if any).
     * Waseel cancels a whole request, so the request is cancelled only with the last line relying on it;
     * the other lines of a cancelled request are released by {@link PreAuthorizationLineReleaseService}.
     */
    @Transactional
    public void cancelForItem(PatientServiceAndProduct item, CancelReason cancelReason) {
        if (item == null) {
            return;
        }

        PreAuthorizationStatus status = item.getPreAuthorizationStatus();
        if (!reliesOnApproval(status)) {
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
            markItemCancelled(item, preAuth);
            return;
        }

        /*
         * The payer cancels a whole request, not one of its items. While other lines still rely on
         * the request, only this line is cancelled: their approval stays, and this line is left out
         * of the claim. The request is cancelled with its last line.
         */
        if (otherLinesRelyOn(patientServiceAndProductRepository.findByPreAuthorizationRequestId(preAuth.getId()), item)) {
            log.info(
                    "[PREAUTH_CANCEL] Other lines still rely on the request — cancelling the line only. preAuthId={} pspId={}",
                    preAuth.getId(),
                    item.getId()
            );
            markItemCancelled(item);
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
            markItemCancelled(item, preAuth);
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

        markItemCancelled(item, preAuth);
    }

    /**
     * A line relies on its request while it waits for, or holds, an approval and is not cancelled.
     * A rejected line does not: the payer authorized nothing for it.
     */
    static boolean otherLinesRelyOn(List<PatientServiceAndProduct> requestLines, PatientServiceAndProduct item) {
        return requestLines.stream()
                .filter(line -> !Objects.equals(line.getId(), item.getId()))
                .filter(line -> line.getPaymentStatus() != PaymentStatus.CANCELLED)
                .map(PatientServiceAndProduct::getPreAuthorizationStatus)
                .anyMatch(PreAuthorizationCancellationService::reliesOnApproval);
    }

    private static boolean reliesOnApproval(PreAuthorizationStatus status) {
        return status == PreAuthorizationStatus.PENDING_APPROVAL
                || status == PreAuthorizationStatus.APPROVED
                || status == PreAuthorizationStatus.PARTIAL;
    }

    private PreAuthorizationRequest resolvePreAuthorization(PatientServiceAndProduct item) {
        if (item.getPreAuthorizationRequestId() == null) {
            return null;
        }

        return preAuthorizationRequestRepository
                .findById(item.getPreAuthorizationRequestId())
                .orElse(null);
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
