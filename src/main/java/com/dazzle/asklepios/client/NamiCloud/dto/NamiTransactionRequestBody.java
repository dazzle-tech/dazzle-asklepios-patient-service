package com.dazzle.asklepios.client.NamiCloud.dto;

import java.math.BigDecimal;

public record NamiTransactionRequestBody(

        BigDecimal amount,

        Integer print,

        String transactionType

) {
}
