package com.dazzle.asklepios.client.NamiCloud;

import com.dazzle.asklepios.client.NamiCloud.dto.*;
import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import com.dazzle.asklepios.service.SocialHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamiCloudClient {
    private static final Logger LOG = LoggerFactory.getLogger(NamiCloudClient.class);

    private final NamiProperties namiProperties;
    private final NamiAuthenticationService namiAuthenticationService;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(namiProperties.baseUrl())
                .build();
    }

    public NamiPurchaseResponse purchase(
            PointOfSaleConfiguration configuration,
            String orderId,
            BigDecimal amount
    ) {
        LOG.info(
                "Sending Purchase Request. OrderId={}, TerminalId={}, Amount={}",
                orderId,
                configuration.getTerminalId(),
                amount
        );
        NamiPurchaseRequest request =
                new NamiPurchaseRequest(
                        orderId,
                        configuration.getTerminalId(),
                        new NamiTransactionRequestBody(
                                amount,
                                1,
                                "PURCHASE"
                        )
                );


        LOG.debug(
                "Purchase Payload={}",
                request
        );
        String token =
                namiAuthenticationService.getToken(
                        configuration.getClientId(),
                        configuration.getClientSecret()
                );
        return restClient()
                .post()
                .uri(
                        namiProperties.purchaseEndpoint()
                )
                .header(
                        "Authorization",
                        token
                )
                .body(request)
                .retrieve()
                .body(
                        NamiPurchaseResponse.class
                );
    }

    public NamiRegisterTerminalResponse registerTerminal(
            PointOfSaleConfiguration configuration
    ) {

        NamiRegisterTerminalRequest request =
                new NamiRegisterTerminalRequest(
                        configuration.getTerminalSerialNo(),
                        configuration.getTerminalType(),
                        configuration.getCounterNumber(),
                        configuration.getClientId(),
                        "17"
                );

        String token =
                namiAuthenticationService.getToken(
                        configuration.getClientId(),
                        configuration.getClientSecret()
                );

        return restClient()
                .post()
                .uri(
                        namiProperties.registerEndpoint()
                )
                .header(
                        "Authorization",
                        token
                )
                .body(request)
                .retrieve()
                .body(
                        NamiRegisterTerminalResponse.class
                );
    }

    public NamiGenerateWebhookUrlResponse generateWebhookUrl(
            PointOfSaleConfiguration configuration
    ) {
        NamiGenerateWebhookUrlRequest request =
                new NamiGenerateWebhookUrlRequest(
                        configuration.getClientId(),
                        configuration.getTerminalId(),
                        configuration.getClientSecret(),
                        namiProperties.webhookBaseUrl(),
                        namiProperties.webhookPath(),
                        namiProperties.webhookSignatureAlgorithm(),
                        namiProperties.webhookTokenExpiryHours(),
                        namiProperties.webhookRetryAttempts()
                );

        String token =
                namiAuthenticationService.getToken(
                        configuration.getClientId(),
                        configuration.getClientSecret()
                );

        return restClient()
                .post()
                .uri(namiProperties.webhookGenerateUrlEndpoint())
                .header("Authorization", token)
                .body(request)
                .retrieve()
                .body(NamiGenerateWebhookUrlResponse.class);
    }

    public NamiRegisterWebhookResponse registerWebhook(
            PointOfSaleConfiguration configuration,
            String callbackUrl
    ) {
        NamiRegisterWebhookRequest request =
                new NamiRegisterWebhookRequest(
                        configuration.getTerminalId(),
                        callbackUrl,
                        configuration.getClientSecret()
                );

        String token =
                namiAuthenticationService.getToken(
                        configuration.getClientId(),
                        configuration.getClientSecret()
                );

        return restClient()
                .post()
                .uri(namiProperties.webhookRegisterEndpoint())
                .header("Authorization", token)
                .body(request)
                .retrieve()
                .body(NamiRegisterWebhookResponse.class);
    }

    public String resolveCallbackUrl(
            NamiGenerateWebhookUrlResponse response
    ) {

        if (response == null) {
            return null;
        }

        return Stream.of(
                        response.callbackUrl(),
                        response.webhookUrl(),
                        response.url(),
                        response.tokenizedUrl()
                )
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public NamiTransactionResponse getTransactionResponse(
            String transactionId,
            PointOfSaleConfiguration configuration
    ) {

        log.info(
                "Fetching Transaction Status. TransactionId={}, TerminalId={}, ClientId={}",
                transactionId,
                configuration.getTerminalId(),
                configuration.getClientId()
        );

        String token =
                namiAuthenticationService.getToken(
                        configuration.getClientId(),
                        configuration.getClientSecret()
                );

        return restClient()
                .get()
                .uri(
                        "/api/payments/response/{id}",
                        transactionId
                )
                .header(
                        "Authorization",
                        token
                )
                .retrieve()
                .body(
                        NamiTransactionResponse.class
                );
    }
}