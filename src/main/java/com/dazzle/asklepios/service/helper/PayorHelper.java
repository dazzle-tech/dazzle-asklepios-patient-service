package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
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

    public PayorDTO findPayorByNphiesId(String payerNphiesId) {
        if (payerNphiesId == null || payerNphiesId.isBlank()) {
            return null;
        }

        try {
            return payorClient.getPayorByNphiesId(payerNphiesId.trim());
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

    public Long resolvePayorId(Long payorId, String payerNphiesId) {
        if (payorId != null && payorId > 0) {
            validatePayorExists(payorId);
            return payorId;
        }

        PayorDTO payor = findPayorByNphiesId(payerNphiesId);
        return payor != null && payor.id() != null && payor.id() > 0 ? payor.id() : null;
    }

    public PayorDTO findPayor(Long payorId, String payerNphiesId) {
        PayorDTO payor = findPayorByNphiesId(payerNphiesId);
        if (payor != null) {
            return payor;
        }

        if (payorId == null || payorId <= 0) {
            return null;
        }

        try {
            return payorClient.getPayorById(payorId);
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

}
