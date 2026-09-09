package com.dazzle.asklepios.client.NamiCloud;

import com.dazzle.asklepios.client.NamiCloud.dto.NamiAuthRequest;
import com.dazzle.asklepios.service.SocialHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamiAuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(NamiAuthenticationService.class);

    private final NamiProperties namiProperties;

    private RestClient restClient() {

        return RestClient.builder()
                .baseUrl(
                        namiProperties.baseUrl()
                )
                .build();
    }

    public String getToken(
            String clientId,
            String clientSecret
    ) {

        LOG.info(
                "Requesting Nami token. BaseUrl={}, ClientId={}",
                namiProperties.baseUrl(),
                namiProperties.clientId()
        );

        NamiAuthRequest request =
                new NamiAuthRequest(
                        clientId,
                        clientSecret
                );

        ResponseEntity<String> response =
                restClient()
                        .post()
                        .uri("/api/auth/getToken")
                        .body(request)
                        .retrieve()
                        .toEntity(String.class);

        String authorizationHeader =
                response.getHeaders()
                        .getFirst("Authorization");

        LOG.info(
                "Nami auth response received. Authorization Header Present={}",
                authorizationHeader != null
        );
      LOG.debug("TOKEN RESPONSE: {}", authorizationHeader);
        if (
                authorizationHeader == null ||
                        authorizationHeader.isBlank()
        ) {

            throw new IllegalStateException(
                    "Authorization header was not returned by Nami authentication service"
            );
        }

        return authorizationHeader;
    }
}
