package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface UserClient {

    @GetMapping("/api/setup/user-departments/user/id")
    Long getUserId(@RequestParam("login") String login);

    @GetMapping("/api/setup/user-departments/user")
    UserDTO getUser(@RequestParam("login") String login);
}