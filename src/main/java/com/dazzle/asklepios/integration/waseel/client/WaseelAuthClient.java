package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "waseelAuthClient",
        url = "${waseel.api.base-url}"
)
public interface WaseelAuthClient {

    @PostMapping("/oauth/authenticate")
    WaseelAuthResponse authenticate(WaseelAuthRequest request);
}