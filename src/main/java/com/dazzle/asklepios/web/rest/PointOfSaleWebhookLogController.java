package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PointOfSaleWebhookLogService;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookLogDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookLogFilterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Slf4j
public class PointOfSaleWebhookLogController {

    private final PointOfSaleWebhookLogService
            pointOfSaleWebhookLogService;

    @GetMapping("/point-of-sale-webhook-logs")
    public ResponseEntity<Page<PointOfSaleWebhookLogDTO>> findAll(

            @RequestParam(
                    required = false
            )
            String externalTransactionId,

            @RequestParam(
                    required = false
            )
            String orderId,

            @RequestParam(
                    required = false
            )
            String responseCode,

            @RequestParam(
                    required = false
            )
            String processingStatus,

            @RequestParam(
                    required = false
            )
            Instant fromDate,

            @RequestParam(
                    required = false
            )
            Instant toDate,
            Pageable pageable
    ) {

        log.info(
                "Webhook Logs Search. TransactionId={}, OrderId={}, ResponseCode={}",
                externalTransactionId,
                orderId,
                responseCode
        );

        PointOfSaleWebhookLogFilterDTO filter =
                new PointOfSaleWebhookLogFilterDTO(

                        externalTransactionId,

                        orderId,

                        responseCode,

                        processingStatus,

                        fromDate,

                        toDate
                );

        return ResponseEntity.ok(
                pointOfSaleWebhookLogService.findAll(
                        filter,
                        pageable
                )
        );
    }
}