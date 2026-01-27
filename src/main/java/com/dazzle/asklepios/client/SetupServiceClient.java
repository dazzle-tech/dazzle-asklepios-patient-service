package com.dazzle.asklepios.client;


import com.dazzle.asklepios.client.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface SetupServiceClient {

    @GetMapping("/api/setup/diagnostic-test-normal-ranges/internal/by-profile-test")
    List<NormalRangeMatchDTO> findAllByProfileTestIdInternal(
            @RequestParam("profileTestId") Long profileTestId
    );



}