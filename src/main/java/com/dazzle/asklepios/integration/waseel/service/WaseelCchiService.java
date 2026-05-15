package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.client.WaseelCchiClient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;

@Service
public class WaseelCchiService {

    private static final Logger LOG = LogManager.getLogger(WaseelCchiService.class);
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

        try {
            LOG.debug("Calling WaseelCchiClient with providerId={} and documentId={}", properties.providerId(), documentId);
            CchiInquiryResponse response = cchiClient.getBeneficiary(
                    "Bearer " + token,
                    properties.providerId(),
                    documentId
            );
            LOG.info("Successfully fetched beneficiary: {}", response);
            return response;
        } catch (Exception e) {
            LOG.error("Error occurred while fetching beneficiary by documentId: {}", documentId, e);
            throw e;
        }
    }
}