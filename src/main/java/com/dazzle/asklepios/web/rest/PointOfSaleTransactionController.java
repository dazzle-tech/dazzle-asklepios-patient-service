package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PointOfSaleTransactionService;
import com.dazzle.asklepios.service.dto.pointOfSale.CreatePointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/point-of-sale-transactions")
@RequiredArgsConstructor
public class PointOfSaleTransactionController {

    private final PointOfSaleTransactionService pointOfSaleTransactionService;

    @PostMapping("/purchase")
    public ResponseEntity<PointOfSaleTransactionDTO> purchase(
            @Valid @RequestBody CreatePointOfSaleTransactionDTO request
    ) {

        PointOfSaleTransactionDTO result =
                pointOfSaleTransactionService.purchase(request);

        return ResponseEntity.ok(result);
    }
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody PointOfSaleWebhookDTO webhook
    ) {

        pointOfSaleTransactionService.processWebhook(
                webhook
        );

        return ResponseEntity.ok().build();
    }
}