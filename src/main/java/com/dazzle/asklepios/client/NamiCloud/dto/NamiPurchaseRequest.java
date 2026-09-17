package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiPurchaseRequest(

        String orderId,

        String terminalId,

        NamiTransactionRequestBody transactionreqbody

) {
}
