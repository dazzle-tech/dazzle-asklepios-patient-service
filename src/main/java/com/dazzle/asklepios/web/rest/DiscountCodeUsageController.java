package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DiscountCodeUsageService;
import com.dazzle.asklepios.web.rest.vm.billing.DiscountCodeUsageVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient")
public class DiscountCodeUsageController {

    private static final Logger LOG = LoggerFactory.getLogger(DiscountCodeUsageController.class);

    private final DiscountCodeUsageService discountCodeUsageService;

    @GetMapping("/billing/encounters/{encounterId}/discount-code-usages")
    public ResponseEntity<List<DiscountCodeUsageVM>> list(@PathVariable Long encounterId) {
        LOG.debug("REST request to list discount code usage encounterId={}", encounterId);
        return ResponseEntity.ok(discountCodeUsageService.findByEncounterId(encounterId));
    }
}
