package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.integration.waseel.config.WaseelFeignConfig;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelAuthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "waseelAuthClient",
        url = "${waseel.api.base-url}",
        configuration = WaseelFeignConfig.class
)
public interface WaseelAuthClient {

    @PostMapping(
            value = "/oauth/authenticate",
            consumes = "application/json"
    )
    WaseelAuthResponse authenticate(@RequestBody WaseelAuthRequest request);
}