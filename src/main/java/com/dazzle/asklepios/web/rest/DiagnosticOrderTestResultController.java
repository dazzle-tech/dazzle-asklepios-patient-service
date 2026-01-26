// src/main/java/com/dazzle/asklepios/web/rest/DiagnosticOrderTestResultController.java
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.DiagnosticOrderTestStatusService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestResponseVM;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestResultController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestResultController.class);

    private final DiagnosticOrderTestResultService service;
    private final DiagnosticOrderTestResultStatusService statusService;
    private final DiagnosticOrderTestResultRepository repository;

    public DiagnosticOrderTestResultController(
            DiagnosticOrderTestResultService service,
            DiagnosticOrderTestResultStatusService statusService,
            DiagnosticOrderTestResultRepository repository
    ) {
        this.service = service;
        this.statusService = statusService;
        this.repository = repository;

    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests_result",
                        "No authenticated user"
                ));
    }

    @PostMapping("/diagnostic-order-tests-results")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> create(@Valid @RequestBody DiagnosticOrderTestResultCreateDTO dto) {
        LOG.debug("[DiagnosticOrderTestResult] CREATE - request received. payload={}", dto);

        DiagnosticOrderTestResult saved = service.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests-results/" + saved.getId()))
                .body(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    @PutMapping("/diagnostic-order-tests-results/{id}")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultUpdateDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] UPDATE - request received. id={} payload={}", id, dto);

        DiagnosticOrderTestResult existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));

        DiagnosticOrderTestResultUpdateDTO fixed = new DiagnosticOrderTestResultUpdateDTO(
                id,
                dto.orderId(),
                dto.orderTestId(),
                dto.profileTestId(),
                dto.resultValueNumber(),
                dto.resultValueText(),
                dto.marker(),
                dto.normalRangeValue()
        );

        DiagnosticOrderTestResult updated = service.update(existing, fixed);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(updated));
    }
    @PostMapping("/diagnostic-order-tests-results/{id}/toggle-review")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> toggleReview(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTestResult saved = statusService.toggleReview(id, username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    @PostMapping("/diagnostic-order-tests-results/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> approve(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTestResult saved = statusService.approve(id, username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    @PostMapping("/diagnostic-order-tests-results/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultRejectDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTestResult saved = statusService.reject(id, username, dto.rejectedReason());
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }


}
