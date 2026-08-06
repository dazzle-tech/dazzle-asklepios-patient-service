package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "waseelEligibilityClient",
        url = "${waseel.api.base-url}"
)
public interface WaseelEligibilityClient {

    
    @PostMapping(
            value = "/eligibilities/providers/{providerId}/request",
            consumes = "application/json",
            produces = "application/json"
    )
    EligibilityResponse checkEligibility(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String providerId,
            @RequestBody EligibilityRequest request
    );
}