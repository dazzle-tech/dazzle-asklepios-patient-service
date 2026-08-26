package com.dazzle.asklepios.service.dto.pointOfSale;

import java.io.Serializable;

public record PointOfSaleWebhookDTO(

        String orderId,

        String transactionId,

        String transactionType,

        String panNumber,

        String transactionAmount,

        String responseCode,

        String responseMessage,

        String rrn,

        String authCode,

        String tid,

        String mid,

        String batchNo,

        String dateTime,

        String cardEntryMode,

        String merchantName,

        String merchantAddress,

        String signature

) implements Serializable {
}
