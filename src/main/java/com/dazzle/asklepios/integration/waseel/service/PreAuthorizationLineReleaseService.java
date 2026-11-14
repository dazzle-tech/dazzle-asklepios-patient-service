package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A cancelled pre-authorization request no longer authorizes its billing lines.
 * <ul>
 *   <li>A line not posted yet still needs a pre-authorization: it leaves the cancelled request and waits,
 *       off the patient account, for a new one (the patient account offers to send it or to cancel the service).</li>
 *   <li>A line already posted keeps its posting and is marked as cancelled pre-authorization.</li>
 *   <li>A cancelled service, a line cancelled on its own, and a rejected line (the payer's decision
 *       stands, the patient still chooses cash or cancel) are left as they are.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PreAuthorizationLineReleaseService {

    private static final Logger LOG = LoggerFactory.getLogger(PreAuthorizationLineReleaseService.class);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    /** @return how many lines left the cancelled request */
    public int release(PreAuthorizationRequest request) {
        List<PatientServiceAndProduct> lines = patientServiceAndProductRepository
                .findByPreAuthorizationRequestId(request.getId())
                .stream()
                .filter(line -> line.getPaymentStatus() != PaymentStatus.CANCELLED)
                .filter(line -> line.getPreAuthorizationStatus() != PreAuthorizationStatus.REJECTED)
                .filter(line -> line.getPreAuthorizationStatus() != PreAuthorizationStatus.CANCELLED)
                .toList();
        if (lines.isEmpty()) {
            return 0;
        }

        lines.forEach(PreAuthorizationLineReleaseService::releaseLine);
        patientServiceAndProductRepository.saveAll(lines);

        LOG.info(
                "[PREAUTH_CANCEL] Lines released from cancelled request. preAuthId={} encounterId={} pspIds={}",
                request.getId(),
                request.getEncounterId(),
                lines.stream().map(PatientServiceAndProduct::getId).toList()
        );
        return lines.size();
    }

    private static void releaseLine(PatientServiceAndProduct line) {
        if (Boolean.TRUE.equals(line.getIsBilled())) {
            line.setPreAuthorizationStatus(PreAuthorizationStatus.CANCELLED);
            line.setPreAuthorizationRequired(false);
            return;
        }
        line.setPreAuthorizationRequestId(null);
        line.setPreAuthorizationReferenceNo(null);
        line.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
        line.setPreAuthorizationRequired(true);
        line.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
    }
}
