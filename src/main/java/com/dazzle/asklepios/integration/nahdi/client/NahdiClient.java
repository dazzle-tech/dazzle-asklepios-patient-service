package com.dazzle.asklepios.integration.nahdi.client;

import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Single entry point for all HTTP communication with Nahdi.
 * Nahdi authenticates through the {@code Provider_ID} / {@code Provider_Token} query parameters.
 */
@Component
public class NahdiClient {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiClient.class);

    private final NahdiProperties nahdiProperties;
    private final RestClient restClient;

    public NahdiClient(NahdiProperties nahdiProperties) {
        this.nahdiProperties = nahdiProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(nahdiProperties.timeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(nahdiProperties.timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(nahdiProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Calls the SendPrescription endpoint.
     *
     * @return the raw response body, as the Nahdi response contract is not documented yet.
     * @throws org.springframework.web.client.RestClientException on transport errors or non-2xx responses.
     */
    public String sendPrescription(NahdiSendPrescriptionRequest request) {
        LOG.debug("[NahdiClient] POST {} presRefId={}", nahdiProperties.sendPrescriptionEndpoint(),
                request.presHead() == null ? null : request.presHead().presRefId());
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(nahdiProperties.sendPrescriptionEndpoint())
                        .queryParam("Provider_ID", "{providerId}")
                        .queryParam("Provider_Token", "{providerToken}")
                        .build(nahdiProperties.providerId(), nahdiProperties.providerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
    }
}
