package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.integration.waseel.client.dto.LanguageResponseVM;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "languageSetupClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface LanguageSetupClient {

    @GetMapping("/api/setup/languages")
    List<LanguageResponseVM> getAllLanguages();

    @GetMapping("/api/setup/languages/by-key/{langKey}")
    LanguageResponseVM getLanguageByLangKey(@PathVariable("langKey") String langKey);
}
