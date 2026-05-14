package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "waseelCchiClient",
        url = "${waseel.api.base-url}"
)
public interface WaseelCchiClient {

    @GetMapping("/beneficiaries/providers/{providerId}/patientKey/{documentId}")
    CchiInquiryResponse getBeneficiary(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String providerId,
            @PathVariable String documentId
    );
}