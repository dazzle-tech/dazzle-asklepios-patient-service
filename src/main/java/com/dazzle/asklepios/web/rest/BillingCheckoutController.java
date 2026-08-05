package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.BillingCheckoutService;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class BillingCheckoutController {

    private final BillingCheckoutService
            billingCheckoutService;

    @PostMapping("/billing/checkout")
    public ResponseEntity<BillingCheckoutResult> checkout(
            @Valid @RequestBody BillingCheckoutRequest request
    ) {
        return ResponseEntity.ok(
                billingCheckoutService.checkout(request)
        );
    }
}