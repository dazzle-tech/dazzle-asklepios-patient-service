package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.OrganizationHolidayDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface OrganizationHolidayClient {
    @GetMapping("/api/setup/organization-holiday/by-date-range")
    List<OrganizationHolidayDTO> getActiveHolidaysInRange(@RequestParam("fromDate") LocalDate fromDate, @RequestParam("toDate") LocalDate toDate, Long facilityId);
}
