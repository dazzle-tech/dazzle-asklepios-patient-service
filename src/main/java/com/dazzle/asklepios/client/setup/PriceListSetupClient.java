package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PriceListSetupClient {

    @GetMapping("/api/setup/price-list-setups/requires-preauth")
    Boolean requiresPreAuthorization(
            @RequestParam Long facilityId,
            @RequestParam Long patientId,
            @RequestParam Long encounterId,
            @RequestParam BillingItemTypes billingItemType,
            @RequestParam Long itemId,
            @RequestParam(required = false) Long patientInsuranceId,
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) BillingCoverageType coverageType,
            @RequestParam Currency currency,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate pricingDate
    );

    @PostMapping("/api/setup/price-list-setups/items/{itemId}/lock-visit-type")
    Void lockVisitType(@PathVariable("itemId") Long itemId);
}
