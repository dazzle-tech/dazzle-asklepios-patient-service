package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BedHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BedHelper.class);

    private final BedClient bedClient;

    public BedHelper(BedClient bedClient) {
        this.bedClient = bedClient;
    }

    public void validateBedExists(Long bedId) {
        try {
            bedClient.existsBed(bedId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Bed not found: " + bedId,
                    "bed",
                    "notfound"
            );
        }
    }

    public void markAsOccupied(Long bedId) {
        try {
            bedClient.markAsOccupied(bedId);
            LOG.info("[MARK_OCCUPIED] success bedId={}", bedId);
            releaseIfTransactionRollsBack(bedId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("[MARK_OCCUPIED] bed not found bedId={}", bedId);
            throw new NotFoundAlertException(
                    "Bed not found: " + bedId,
                    "bed",
                    "notfound"
            );
        } catch (FeignException ex) {
            LOG.error("[MARK_OCCUPIED] failed bedId={} status={}", bedId, ex.status(), ex);
            throw new BadRequestAlertException(
                    "Bed is not available to be occupied: " + bedId,
                    "bed",
                    "bed.notAvailable"
            );
        }
    }

    // If the assignment rolls back, return the bed to READY so it is not left OCCUPIED with no patient
    private void releaseIfTransactionRollsBack(Long bedId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    releaseOccupiedQuietly(bedId);
                }
            }
        });
    }

    // Best effort: never throws over the original error
    private void releaseOccupiedQuietly(Long bedId) {
        try {
            bedClient.releaseOccupied(bedId);
            LOG.warn("[RELEASE_OCCUPIED] assignment rolled back, bed returned to READY bedId={}", bedId);
        } catch (RuntimeException ex) {
            LOG.error("[RELEASE_OCCUPIED] could not return bed to READY after rollback bedId={}", bedId, ex);
        }
    }

    public void markAsInCleaning(Long bedId) {
        if (bedId == null) {
            return;
        }

        try {
            bedClient.markAsInCleaning(bedId);
            LOG.info("[MARK_IN_CLEANING] success bedId={}", bedId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("[MARK_IN_CLEANING] bed not found bedId={}", bedId);
            throw new NotFoundAlertException(
                    "Bed not found: " + bedId,
                    "bed",
                    "notfound"
            );
        } catch (FeignException ex) {
            LOG.error("[MARK_IN_CLEANING] failed bedId={} status={}", bedId, ex.status(), ex);
            throw new BadRequestAlertException(
                    "Failed to mark bed as IN_CLEANING: " + bedId,
                    "bed",
                    "status.update.failed"
            );
        }
    }

}
