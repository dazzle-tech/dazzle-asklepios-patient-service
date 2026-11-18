package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.TpaResolutionDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import java.util.List;
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

    /**
     * Best-effort legacy payor id for an insurance write. Unlike {@link #resolvePayorId}
     * it never throws: payers.id is the real reference now and payor is being retired.
     */
    public Long resolveLegacyPayorId(Long payorId, String payerNphiesId) {
        try {
            return resolvePayorId(payorId, payerNphiesId);
        } catch (RuntimeException ex) {
            return null;
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
        String nphiesId = firstNonBlank(payerNphiesId);
        if (nphiesId == null && !isExistingPayor(payorId)) {
            nphiesId = nphiesIdFromPayerRow(payorId);
        }

        if (nphiesId != null) {
            PayorDTO payor = findPayorByNphiesId(nphiesId);
            if (!hasId(payor)) {
                payor = ensurePayorFromNphiesId(nphiesId);
            }
            if (hasId(payor)) {
                return payor.id();
            }
        }

        if (isExistingPayor(payorId)) {
            return payorId;
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

    /**
     * NPHIES id of the insurance's payer: the stored value, else the payers row, else the
     * legacy payor row (for old insurances saved before payers.id was written).
     */
    public String resolvePayerNphiesId(PatientInsurance insurance) {
        if (insurance == null) {
            return null;
        }
        String stored = firstNonBlank(insurance.getPayerNphiesId());
        if (stored != null) {
            return stored;
        }
        NphiesPayerDTO payer = findPayerRow(insurance.getNphiesPayerId(), null);
        if (payer != null && firstNonBlank(payer.nphiesId()) != null) {
            return payer.nphiesId().trim();
        }
        if (insurance.getPayorId() == null) {
            return null;
        }
        PayorDTO payor = findPayor(insurance.getPayorId(), null);
        return payor == null ? null : firstNonBlank(payor.nphiesId());
    }

    /**
     * Waseel flag for this insurance, read from the payers table (formerly nphies_payers);
     * falls back to the legacy payor row when the payer row cannot be found.
     */
    public boolean isWaseelEnabled(PatientInsurance insurance) {
        if (insurance == null) {
            return false;
        }
        NphiesPayerDTO payer = findPayerRow(insurance.getNphiesPayerId(), insurance.getPayerNphiesId());
        if (payer != null && payer.isWaseelEnabled() != null) {
            return payer.isWaseelEnabled();
        }
        PayorDTO payor = findPayor(insurance.getPayorId(), insurance.getPayerNphiesId());
        return payor != null && Boolean.TRUE.equals(payor.isWaseelEnabled());
    }

    private NphiesPayerDTO findPayerRow(Long payerId, String payerNphiesId) {
        try {
            if (payerId != null && payerId > 0) {
                return nphiesPayerClient.getNphiesPayerById(payerId);
            }
            String nphiesId = firstNonBlank(payerNphiesId);
            if (nphiesId == null) {
                return null;
            }
            List<NphiesPayerDTO> payers = nphiesPayerClient.getNphiesPayersByNphiesId(nphiesId, 0, 1);
            return payers == null || payers.isEmpty() ? null : payers.get(0);
        } catch (feign.FeignException ex) {
            if (isNotFound(ex)) {
                return null;
            }
            throw ex;
        }
    }

    public TpaResolutionDTO resolveTpa(String payerNphiesId, String policyNumber, String preferredTpaNphiesId) {
        return payorClient.resolveTpa(payerNphiesId, policyNumber, preferredTpaNphiesId);
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
