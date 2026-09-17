package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class PayorHelper {

    private final PayorClient payorClient;
    private final NphiesPayerClient nphiesPayerClient;

    public PayorHelper(PayorClient payorClient, NphiesPayerClient nphiesPayerClient) {
        this.payorClient = payorClient;
        this.nphiesPayerClient = nphiesPayerClient;
    }

    public void validatePayorExists(Long payorId) {
        if (!isExistingPayor(payorId)) {
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
        } catch (feign.FeignException ex) {
            if (isNotFound(ex)) {
                return null;
            }
            throw ex;
        }
    }

    public Long resolvePayorId(Long payorId, String payerNphiesId) {
        if (isExistingPayor(payorId)) {
            return payorId;
        }

        String nphiesId = firstNonBlank(payerNphiesId, nphiesIdFromPayerRow(payorId));
        PayorDTO payor = findPayorByNphiesId(nphiesId);
        if (hasId(payor)) {
            return payor.id();
        }

        payor = ensurePayorFromNphiesId(nphiesId);
        if (hasId(payor)) {
            return payor.id();
        }

        if (payorId != null && payorId > 0) {
            throw new NotFoundAlertException(
                    "Payor not found: " + payorId,
                    "payor",
                    "notfound"
            );
        }

        return null;
    }

    public PayorDTO findPayor(Long payorId, String payerNphiesId) {
        String nphiesId = firstNonBlank(payerNphiesId, nphiesIdFromPayerRow(payorId));

        PayorDTO payor = findPayorByNphiesId(nphiesId);
        if (payor != null) {
            return payor;
        }

        if (payorId != null && payorId > 0) {
            try {
                return payorClient.getPayorById(payorId);
            } catch (feign.FeignException ex) {
                if (!isNotFound(ex)) {
                    throw ex;
                }
            }
        }

        return ensurePayorFromNphiesId(nphiesId);
    }

    private PayorDTO ensurePayorFromNphiesId(String payerNphiesId) {
        if (payerNphiesId == null || payerNphiesId.isBlank()) {
            return null;
        }

        try {
            return payorClient.ensurePayorFromNphiesId(payerNphiesId.trim());
        } catch (feign.FeignException.NotFound | feign.FeignException.BadRequest ex) {
            return null;
        } catch (feign.FeignException ex) {
            if (ex.status() == 404 || ex.status() == 400) {
                return null;
            }
            throw ex;
        }
    }

    private boolean isExistingPayor(Long payorId) {
        if (payorId == null || payorId <= 0) {
            return false;
        }

        try {
            payorClient.existsPayor(payorId);
            return true;
        } catch (feign.FeignException ex) {
            if (isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private String nphiesIdFromPayerRow(Long nphiesPayerId) {
        if (nphiesPayerId == null || nphiesPayerId <= 0) {
            return null;
        }

        try {
            NphiesPayerDTO payer = nphiesPayerClient.getNphiesPayerById(nphiesPayerId);
            if (payer == null || payer.nphiesId() == null || payer.nphiesId().isBlank()) {
                return null;
            }
            return payer.nphiesId().trim();
        } catch (feign.FeignException ex) {
            if (isNotFound(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private static boolean hasId(PayorDTO payor) {
        return payor != null && payor.id() != null && payor.id() > 0;
    }

    private static boolean isNotFound(feign.FeignException ex) {
        return ex instanceof feign.FeignException.NotFound || ex.status() == 404;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
