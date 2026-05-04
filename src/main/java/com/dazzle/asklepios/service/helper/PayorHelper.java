package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class PayorHelper {

    private final PayorClient payorClient;

    public PayorHelper(PayorClient payorClient) {
        this.payorClient = payorClient;
    }

    public void validatePayorExists(Long payorId) {
        try {
            payorClient.existsPayor(payorId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Payor not found: " + payorId,
                    "payor",
                    "notfound"
            );
        }
    }

}
