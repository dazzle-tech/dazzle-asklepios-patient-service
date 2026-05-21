package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.PayorPlanClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SetupInsuranceLookupService {

    private final PayorClient payorClient;
    private final PayorPlanClient payorPlanClient;


    public Optional<PayorDTO> findPayorByNphiesId(String nphiesId) {
        if (isBlank(nphiesId)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(
                    payorClient.getPayorByNphiesId(nphiesId.trim())
            );
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<PayorPlanDTO> findPayorPlanByCchiMatch(
            Long payorId,
            String coverageType,
            String networkId,
            String policyClassName
    ) {
        if (payorId == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(
                    payorPlanClient.getPayorPlanByCchiMatch(
                            payorId,
                            clean(coverageType),
                            clean(networkId),
                            clean(policyClassName)
                    )
            );
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();

        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


}