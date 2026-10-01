package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiRegisterWebhookResponse(

        String status,

        String message,

        String terminalId,

        String callbackUrl

) {
}

