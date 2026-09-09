package com.dazzle.asklepios.service.dto.pointOfSale;

import java.io.Serializable;
import java.time.Instant;

public record PointOfSaleWebhookLogDTO(

        Long id,

        Long transactionId,

        String orderId,

        String externalTransactionId,

        String responseCode,

        String responseMessage,

        String transactionStatus,

        String rrn,

        String authCode,

        String terminalId,

        String merchantId,

        String processingStatus,

        Boolean processed,

        Instant createdDate

) implements Serializable {
}