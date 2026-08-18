package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.InsurancePriceListCoverageService;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckRequest;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient")
public class InsurancePriceListCoverageController {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePriceListCoverageController.class);

    private final InsurancePriceListCoverageService insurancePriceListCoverageService;

    /**
     * Lets the UI warn before ordering. Create/submit APIs still enforce the
     * same rule and return 409 {@code insurance.item.notInPriceList} unless
     * the doctor confirms cash billing.
     */
    @PostMapping("/insurance-coverage/price-list-check")
    public ResponseEntity<InsurancePriceListCoverageCheckResult> check(
            @Valid @RequestBody InsurancePriceListCoverageCheckRequest request
    ) {
        LOG.debug("REST insurance price-list coverage check payload={}", request);
        return ResponseEntity.ok(insurancePriceListCoverageService.check(request));
    }
}
