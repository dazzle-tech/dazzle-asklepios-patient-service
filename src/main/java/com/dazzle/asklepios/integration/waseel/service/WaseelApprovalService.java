package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WaseelApprovalService {

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;

    public WaseelApprovalService(
            RestTemplate restTemplate,
            WaseelTokenService tokenService,
            WaseelApiProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public ApprovalResponse requestApproval(ApprovalRequest request) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/request";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<ApprovalRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApprovalResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ApprovalResponse.class
        );

        return response.getBody();
    }

    public ApprovalResponse getExternalApproval(String requestId) {

        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/nphies-rest-external/providers/"
                + properties.providerId()
                + "/external/approval?requestId="
                + requestId;

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(token);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<ApprovalResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        ApprovalResponse.class
                );

        return response.getBody();
    }

    public ApprovalResponse cancelApproval(ApprovalCancelRequest request) {

        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/cancel/request";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<ApprovalCancelRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<ApprovalResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        ApprovalResponse.class
                );

        return response.getBody();
    }
    public ApprovalResponse requestApproval(WaseelApprovalRequest request) {
        String token = tokenService.getToken();

        String url = properties.baseUrl()
                + "/approvals/providers/"
                + properties.providerId()
                + "/approval/request";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<WaseelApprovalRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApprovalResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ApprovalResponse.class
        );

        return response.getBody();
    }
}