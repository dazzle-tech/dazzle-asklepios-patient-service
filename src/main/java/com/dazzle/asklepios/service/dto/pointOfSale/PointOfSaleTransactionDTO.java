package com.dazzle.asklepios.service.dto.pointOfSale;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleSourceType;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PointOfSaleTransactionDTO(

        Long id,

        Long patientId,

        Long patientPaymentId,

        Long configurationId,

        PointOfSaleSourceType sourceType,

        Long sourceReferenceId,

        String orderId,

        String externalTransactionCode,

        PointOfSaleTransactionType transactionType,

        PointOfSaleTransactionStatus transactionStatus,

        BigDecimal amount,

        String currencyCode,

        String responseCode,

        String responseMessage,

        String rrn,

        String authCode,

        String terminalId,

        String merchantId,

        String batchNo,

        String paymentMethod,

        String schemeLabel,

        Instant transactionDate,

        Boolean webhookReceived,
        String externalTransactionId

) implements Serializable {
}
