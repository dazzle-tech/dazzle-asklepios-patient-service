package com.dazzle.asklepios.integration.waseel.client;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CountryName;
import com.dazzle.asklepios.integration.waseel.client.dto.CountryDistrictResponseVM;
import com.dazzle.asklepios.integration.waseel.client.dto.CountryResponseVM;
import com.dazzle.asklepios.integration.waseel.client.dto.DistrictCommunityResponseVM;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Pageable;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "waseelSetupClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface CountrySetupClient {

    @GetMapping("/api/setup/country/by-name/{name}")
    List<CountryResponseVM> getCountryByName(
            @PathVariable("name") CountryName name,
            @SpringQueryMap Pageable pageable
    );

    @GetMapping("/api/setup/country/{countryId}/district/by-name")
    List<CountryDistrictResponseVM> getDistrictByName(
            @PathVariable("countryId") Long countryId,
            @RequestParam(value = "name", required = false) String name,
            @SpringQueryMap Pageable pageable
    );

    @GetMapping("/api/setup/district/{districtId}/community/by-name")
    List<DistrictCommunityResponseVM> getCommunityByName(
            @PathVariable("districtId") Long districtId,
            @RequestParam(value = "name", required = false) String name,
            @SpringQueryMap Pageable pageable
    );
}