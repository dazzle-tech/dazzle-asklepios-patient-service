package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.service.BillingRefundService;
import com.dazzle.asklepios.service.dto.billing.BillingRefundRequest;
import com.dazzle.asklepios.service.dto.billing.BillingRefundResult;
import com.dazzle.asklepios.service.dto.billing.BillingRefundReversalResult;
import com.dazzle.asklepios.service.dto.billing.RefundReversalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class BillingRefundController {

    private final BillingRefundService
            billingRefundService;

    @PostMapping("/billing/refund")
    public ResponseEntity<BillingRefundResult> refund(
            @Valid
            @RequestBody
            BillingRefundRequest request
    ) {
        return ResponseEntity.ok(
                billingRefundService
                        .refundAvailableBalance(request)
        );
    }

    @PostMapping("/billing/refunds/{refundId}/reverse")
    public ResponseEntity<BillingRefundReversalResult>
    reverseRefund(
            @PathVariable Long refundId,

            @Valid
            @RequestBody
            RefundReversalRequest request
    ) {
        return ResponseEntity.ok(
                billingRefundService.reverseRefund(
                        refundId,
                        request.amount(),
                        request.reason(),
                        request.reversedBy(),
                        request.requestId(),
                        request.sourceChannel()
                )
        );
    }


}