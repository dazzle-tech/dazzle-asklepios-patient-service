package com.dazzle.asklepios.client.NamiCloud.dto;

import java.time.LocalDateTime;

public record NamiPurchaseResponse(

        String transactionid,

        LocalDateTime transactionDate,

        String transactionType,

        String statusMessage,

        String status,

        Integer statusCode,

        String orderId

) {
}
