package com.dazzle.asklepios.service.dto.pointOfSale;

import java.time.Instant;

public record PointOfSaleWebhookLogFilterDTO(

        String externalTransactionId,

        String orderId,

        String responseCode,

        String processingStatus,

        Instant fromDate,

        Instant toDate

) {
}