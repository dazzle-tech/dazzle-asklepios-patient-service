package com.dazzle.asklepios.client.NamiCloud;

import com.dazzle.asklepios.client.NamiCloud.dto.NamiPurchaseRequest;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiPurchaseResponse;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiRegisterTerminalRequest;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiRegisterTerminalResponse;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiTransactionRequestBody;
import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamiCloudClient {

    private final NamiProperties namiProperties;

    public NamiPurchaseResponse purchase(
            PointOfSaleConfiguration configuration,
            String orderId,
            BigDecimal amount
    ) {

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

        RestClient restClient = RestClient.builder()
                .baseUrl(namiProperties.baseUrl())
                .build();

        return restClient.post()
                .uri(namiProperties.purchaseEndpoint())
                .body(request)
                .retrieve()
                .body(NamiPurchaseResponse.class);
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

        RestClient restClient = RestClient.builder()
                .baseUrl(namiProperties.baseUrl())
                .build();

        return restClient.post()
                .uri(namiProperties.registerEndpoint())
                .body(request)
                .retrieve()
                .body(NamiRegisterTerminalResponse.class);
    }
}
