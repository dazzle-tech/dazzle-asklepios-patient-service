package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelCchiClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import org.springframework.stereotype.Service;

@Service
public class WaseelCchiService {

    private final WaseelCchiClient cchiClient;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;

    public WaseelCchiService(
            WaseelCchiClient cchiClient,
            WaseelTokenService tokenService,
            WaseelApiProperties properties
    ) {
        this.cchiClient = cchiClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public CchiInquiryResponse fetchBeneficiaryByDocumentId(String documentId) {
        String token = tokenService.getToken();

        return cchiClient.getBeneficiary(
                "Bearer " + token,
                properties.providerId(),
                documentId
        );
    }
}