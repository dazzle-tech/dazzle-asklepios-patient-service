package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PointOfSaleTransactionService;
import com.dazzle.asklepios.service.dto.pointOfSale.CreatePointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PointOfSaleTransactionController {
    private static final Logger LOG = LoggerFactory.getLogger(PointOfSaleTransactionController.class);

    private final PointOfSaleTransactionService pointOfSaleTransactionService;


    @PostMapping("/point-of-sale-transactions/purchase")
    public ResponseEntity<PointOfSaleTransactionDTO> purchase(
            @Valid @RequestBody CreatePointOfSaleTransactionDTO request
    ) {

        LOG.info(
                "Purchase API called. PatientId={}, Amount={}, SourceType={}, SourceReferenceId={}",
                request.patientId(),
                request.amount(),
                request.sourceType(),
                request.sourceReferenceId()
        );

        PointOfSaleTransactionDTO result =
                pointOfSaleTransactionService.purchase(request);

        LOG.info(
                "Purchase API completed. TransactionId={}, OrderId={}, Status={}",
                result.id(),
                result.orderId(),
                result.transactionStatus()
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/point-of-sale-transactions/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody PointOfSaleWebhookDTO webhook
    ) {

        LOG.info(
                "Webhook received. TransactionId={}, ResponseCode={}, RRN={}",
                webhook.transactionId(),
                webhook.responseCode(),
                webhook.rrn()
        );

        pointOfSaleTransactionService.processWebhook(
                webhook
        );

        LOG.info(
                "Webhook processed successfully. TransactionId={}",
                webhook.transactionId()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping(
            "/transactions/{id}/refresh-status"
    )
    public ResponseEntity<PointOfSaleTransactionDTO>
    refreshStatus(
            @PathVariable Long id
    ) {

        LOG.info(
                "Refresh transaction requested. InternalTransactionId={}",
                id
        );

        PointOfSaleTransactionDTO result =
                pointOfSaleTransactionService
                        .refreshTransactionStatus(id);

        LOG.info(
                "Refresh transaction completed. InternalTransactionId={}, Status={}, ResponseCode={}",
                result.id(),
                result.transactionStatus(),
                result.responseCode()
        );

        return ResponseEntity.ok(
                result
        );
    }
}