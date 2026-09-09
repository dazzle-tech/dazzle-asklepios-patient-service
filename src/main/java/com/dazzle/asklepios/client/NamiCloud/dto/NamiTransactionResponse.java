package com.dazzle.asklepios.client.NamiCloud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NamiTransactionResponse(

        String transactionId,

        String transactionType,

        String responseCode,

        String responseMessage,

        String panNumber,

        BigDecimal transactionAmount,

        String bussCode,

        String stanNo,

        LocalDateTime dateTime,

        String cardExpDate,

        String rrn,

        String authCode,

        String tid,

        String mid,

        String batchNo,

        String aid,

        String applicationCryptogram,

        String cid,

        String cvr,

        String tvr,

        String tsi,

        String kernalId,

        String par,

        String suffix,

        String cardEntryMode,

        String merchantCategoryCode,

        String terminalTransactionType,

        String schemeLabel,

        String productInfo,

        String applicationVersion,

        String disclaimer,

        String merchantName,

        String merchantAddress,

        String merchantNameArabicHex,

        String merchantAddressArabicHex,

        String ecrTransactionReferenceNumber,

        String signature,

        LocalDateTime createdAt

) {
}