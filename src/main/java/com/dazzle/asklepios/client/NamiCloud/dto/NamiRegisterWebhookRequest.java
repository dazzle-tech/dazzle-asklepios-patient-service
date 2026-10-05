package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiRegisterWebhookRequest(

        String terminalId,

        String callbackUrl,

        String secretKey

) {
}

