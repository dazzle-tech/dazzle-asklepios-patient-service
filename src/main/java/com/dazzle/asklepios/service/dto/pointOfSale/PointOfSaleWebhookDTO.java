package com.dazzle.asklepios.service.dto.pointOfSale;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PointOfSaleWebhookDTO(

        String orderId,

        String transactionId,

        String transactionType,

        @JsonAlias("pan")
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

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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