package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiGenerateWebhookUrlResponse(

        String status,

        String message,

        String callbackUrl,

        String webhookUrl,

        String url,

        String tokenizedUrl

) {
}

