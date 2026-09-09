package com.dazzle.asklepios.service.dto.pointOfSale;

import java.io.Serializable;
import java.time.LocalDateTime;

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

        String stanNo,

        LocalDateTime dateTime,

        String cardEntryMode,

        String schemeLabel,

        String productInfo,

        String applicationVersion,

        String ecrTransactionReferenceNumber,

        String merchantName,

        String merchantAddress,

        String signature

) implements Serializable {
}