package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiGenerateWebhookUrlRequest(

        String clientId,

        String terminalId,

        String secretKey,

        String baseUrl,

        String webhookPath,

        String signatureAlgorithm,

        Integer tokenExpiryHours,

        Integer retryAttempts

) {
}

