package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.BillingCancellationService;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationResult;
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
public class BillingCancellationController {

    private final BillingCancellationService
            billingCancellationService;

    @PostMapping("/billing/cancel-service")
    public ResponseEntity<BillingCancellationResult>
    cancelService(
            @Valid
            @RequestBody
            BillingCancellationRequest request
    ) {
        return ResponseEntity.ok(
                billingCancellationService
                        .cancelPatientService(request)
        );
    }
}