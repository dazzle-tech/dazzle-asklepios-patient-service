package com.dazzle.asklepios.integration.waseel.controller;


import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.service.WaseelPreAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class WaseelPreAuthorizationController {

    private final WaseelPreAuthorizationService service;

    @GetMapping("/internal/waseel/pre-authorizations/search")
    public Object search(@RequestParam("requestId") Long requestId) {
        return service.search(requestId);
    }

    @PostMapping("/internal/waseel/pre-authorizations/communication")
    public Object communicate(@RequestBody PreAuthorizationCommunicationRequest request) {
        return service.communicate(request);
    }

    @PostMapping("/internal/waseel/pre-authorizations/cancel")
    public Object cancel(@RequestBody PreAuthorizationCancelRequest request) {
        return service.cancel(request);
    }
}