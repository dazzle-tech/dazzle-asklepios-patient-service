package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class BedHelper {

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

}
