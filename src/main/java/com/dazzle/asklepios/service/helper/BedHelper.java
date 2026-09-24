package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
