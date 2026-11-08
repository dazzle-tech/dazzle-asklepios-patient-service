package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PreAuthorizationAlertClose;
import com.dazzle.asklepios.service.PreAuthorizationAlertCloseService;
import com.dazzle.asklepios.service.dto.PreAuthorizationAlertCloseRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/pre-authorization-alerts")
public class PreAuthorizationAlertCloseController {

    private final PreAuthorizationAlertCloseService service;

    public PreAuthorizationAlertCloseController(PreAuthorizationAlertCloseService service) {
        this.service = service;
    }

    @PostMapping("/close")
    public ResponseEntity<List<PreAuthorizationAlertClose>> close(
            @Valid @RequestBody PreAuthorizationAlertCloseRequest request
    ) {
        return ResponseEntity.ok(service.record(request));
    }
}
