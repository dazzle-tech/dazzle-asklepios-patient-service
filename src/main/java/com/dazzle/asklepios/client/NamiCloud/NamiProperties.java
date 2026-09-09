package com.dazzle.asklepios.client.NamiCloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.pos.nami")
public record NamiProperties(

        String baseUrl,

        String clientId,

        String clientSecret,

        String purchaseEndpoint,

        String refundEndpoint,

        String reversalEndpoint,

        String registerEndpoint

) {
}
