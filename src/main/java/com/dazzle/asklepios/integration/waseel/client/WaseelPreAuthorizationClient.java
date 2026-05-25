package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.config.WaseelFeignConfig;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "waseel-pre-authorization-client",
        url = "${waseel.api.base-url}",
        configuration = WaseelFeignConfig.class
)
public interface WaseelPreAuthorizationClient {


    @GetMapping("/nphies-rest-external/providers/{providerId}/external/approval")
    PreAuthorizationSearchResponse searchPreAuthorization(
            @PathVariable String providerId,
            @RequestParam("requestId") Long requestId
    );

    @PostMapping("/poll-management/providers/{providerId}/communication")
    PreAuthorizationCommunicationResponse sendCommunication(
            @PathVariable String providerId,
            @RequestBody PreAuthorizationCommunicationRequest request
    );

    @PostMapping("/approvals/providers/{providerId}/approval/cancel/request")
    PreAuthorizationCancelResponse cancelPreAuthorization(
            @PathVariable String providerId,
            @RequestBody PreAuthorizationCancelRequest request
    );

}