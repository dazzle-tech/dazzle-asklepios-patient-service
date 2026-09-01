package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NphiesPayerHelper {

    private final NphiesPayerClient nphiesPayerClient;
    private final PayorHelper payorHelper;

    public NphiesPayerHelper(
            NphiesPayerClient nphiesPayerClient,
            PayorHelper payorHelper
    ) {
        this.nphiesPayerClient = nphiesPayerClient;
        this.payorHelper = payorHelper;
    }

    public NphiesPayerDTO findById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }

        try {
            return nphiesPayerClient.getNphiesPayerById(id);
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

    public NphiesPayerDTO findByNphiesId(String payerNphiesId) {
        if (payerNphiesId == null || payerNphiesId.isBlank()) {
            return null;
        }

        try {
            List<NphiesPayerDTO> payers =
                    nphiesPayerClient.getNphiesPayersByNphiesId(payerNphiesId.trim(), 0, 1);

            if (payers == null || payers.isEmpty()) {
                return null;
            }

            return payers.stream()
                    .filter(payer -> payer != null && Boolean.TRUE.equals(payer.isActive()))
                    .findFirst()
                    .orElse(payers.get(0));
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

    /**
     * Price lists in setup reference {@code nphies_payers.id}, while patient
     * insurance stores {@code payor.id}. Resolve the NPHIES payer row used for
     * price-list matching via the shared NPHIES identifier.
     */
    public Long resolvePriceListPayerId(
            Long payorId,
            String payerNphiesId
    ) {
        String nphiesId = payerNphiesId;

        if (nphiesId == null || nphiesId.isBlank()) {
            var payor = payorHelper.findPayor(payorId, null);
            if (payor != null && payor.nphiesId() != null) {
                nphiesId = payor.nphiesId();
            }
        }

        NphiesPayerDTO nphiesPayer = findByNphiesId(nphiesId);
        if (nphiesPayer == null || nphiesPayer.id() == null || nphiesPayer.id() <= 0) {
            return null;
        }

        return nphiesPayer.id();
    }

    public String resolvePayerDisplayName(String payerNphiesId, String existingPayerName) {
        if (existingPayerName != null && !existingPayerName.isBlank()) {
            return existingPayerName.trim();
        }

        NphiesPayerDTO payer = findByNphiesId(payerNphiesId);
        if (payer == null) {
            return null;
        }

        if (payer.nameEn() != null && !payer.nameEn().isBlank()) {
            return payer.nameEn().trim();
        }

        if (payer.nameAr() != null && !payer.nameAr().isBlank()) {
            return payer.nameAr().trim();
        }

        return null;
    }
}
