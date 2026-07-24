package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.BillingPaymentService;
import com.dazzle.asklepios.service.BillingTransactionService;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationResult;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutRequest;
import com.dazzle.asklepios.service.dto.billing.BillingCheckoutResult;
import com.dazzle.asklepios.service.dto.billing.BillingPaymentResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalResult;
import com.dazzle.asklepios.service.dto.billing.CreateAdvancePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing")
public class BillingTransactionController {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingTransactionController.class
            );

    private final BillingTransactionService
            billingTransactionService;

    private final BillingPaymentService
            billingPaymentService;

    @PostMapping("/payments/advance")
    public ResponseEntity<BillingPaymentResult>
    createAdvancePayment(
            @Valid
            @RequestBody
            @NotNull
            CreateAdvancePaymentRequest request
    ) {
        LOG.debug(
                "REST request to create advance billing payment patientId={} encounterId={} amount={} requestId={}",
                request.patientId(),
                request.encounterId(),
                request.amount(),
                request.requestId()
        );

        BillingPaymentResult result =
                billingPaymentService
                        .createAdvancePayment(
                                request
                        );

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/billing/payments/"
                                        + result.paymentId()
                        )
                )
                .body(result);
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<BillingPaymentResult>
    getPayment(
            @PathVariable("paymentId")
            @NotNull
            Long paymentId
    ) {
        LOG.debug(
                "REST request to get billing payment paymentId={}",
                paymentId
        );

        return ResponseEntity.ok(
                billingPaymentService
                        .findById(paymentId)
        );
    }

    @PostMapping("/checkout")
    public ResponseEntity<BillingCheckoutResult>
    checkout(
            @Valid
            @RequestBody
            @NotNull
            BillingCheckoutRequest request
    ) {
        LOG.debug(
                "REST request to checkout billing charge chargeId={} requestId={}",
                request.chargeId(),
                request.requestId()
        );

        return ResponseEntity.ok(
                billingTransactionService
                        .checkout(request)
        );
    }

    @PostMapping("/cancel-service")
    public ResponseEntity<BillingCancellationResult>
    cancelPatientService(
            @Valid
            @RequestBody
            @NotNull
            BillingCancellationRequest request
    ) {
        LOG.debug(
                "REST request to cancel billing service pspId={} requestId={}",
                request.patientServiceProductId(),
                request.requestId()
        );

        return ResponseEntity.ok(
                billingTransactionService
                        .cancelPatientService(
                                request
                        )
        );
    }

    @PostMapping("/refund")
    public ResponseEntity<BillingRefundResult>
    refund(
            @Valid
            @RequestBody
            @NotNull
            BillingRefundRequest request
    ) {
        LOG.debug(
                "REST request to refund billing wallet patientId={} amount={} requestId={}",
                request.patientId(),
                request.requestedAmount(),
                request.requestId()
        );

        return ResponseEntity.ok(
                billingTransactionService
                        .refund(request)
        );
    }

    @PostMapping("/refund/reverse")
    public ResponseEntity<BillingRefundReversalResult>
    reverseRefund(
            @Valid
            @RequestBody
            @NotNull
            BillingRefundReversalRequest request
    ) {
        LOG.debug(
                "REST request to reverse billing refund refundId={} amount={} requestId={}",
                request.refundId(),
                request.amount(),
                request.requestId()
        );

        return ResponseEntity.ok(
                billingTransactionService
                        .reverseRefund(request)
        );
    }
}
