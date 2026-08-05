package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.config.WaseelFeignConfig;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "waseelCchiClient",
        url = "${waseel.api.base-url}",
        configuration = WaseelFeignConfig.class
)
public interface WaseelCchiClient {

    @GetMapping(
            value = "/beneficiaries/providers/{providerId}/patientKey/{documentId}/systemType/{systemType}",
            produces = "application/json"
    )
    CchiInquiryResponse getBeneficiary(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Accept") String accept,
            @RequestHeader("User-Agent") String userAgent,
            @PathVariable("providerId") String providerId,
            @PathVariable("documentId") String documentId,
            @PathVariable("systemType") String systemType
    );
}