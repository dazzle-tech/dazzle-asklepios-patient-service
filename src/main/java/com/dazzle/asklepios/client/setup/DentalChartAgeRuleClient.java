package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.DentalChartAgeRuleDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(
        name = "setupServiceClient",
        contextId = "dentalChartAgeRuleClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface DentalChartAgeRuleClient {

    @GetMapping("/api/setup/dental-chart-age-rules/resolve")
    ResponseEntity<DentalChartAgeRuleDTO> resolveByBirthDate(
            @RequestParam("birthDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate birthDate,
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOfDate
    );
}
