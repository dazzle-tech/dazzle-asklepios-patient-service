package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NphiesPayerHelper {

    private final NphiesPayerClient nphiesPayerClient;

    public NphiesPayerHelper(NphiesPayerClient nphiesPayerClient) {
        this.nphiesPayerClient = nphiesPayerClient;
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
