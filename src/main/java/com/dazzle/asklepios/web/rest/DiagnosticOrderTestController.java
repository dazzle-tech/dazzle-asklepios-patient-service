// src/main/java/com/dazzle/asklepios/web/rest/DiagnosticOrderTestController.java
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestResponseVM;
import org.springdoc.core.annotations.ParameterObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestController.class);

    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticOrderTestController(DiagnosticOrderTestService diagnosticOrderTestService,
                                         DiagnosticOrderTestRepository diagnosticOrderTestRepository) {
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    @PostMapping("/diagnostic-order-tests")
    public ResponseEntity<DiagnosticOrderTestResponseVM> create(@Valid @RequestBody DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("REST create DiagnosticOrderTest payload={}", dto);

        DiagnosticOrderTest saved = diagnosticOrderTestService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests/" + saved.getId()))
                .body(DiagnosticOrderTestResponseVM.ofEntity(saved));
    }

    @PutMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> update(@PathVariable Long id, @Valid @RequestBody DiagnosticOrderTestUpdateDTO dto) {
        LOG.debug("REST update DiagnosticOrderTest id={} payload={}", id, dto);

        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrderTest not found with id " + id,
                        "diagnostic_order_tests",
                        "notfound"
                ));

        DiagnosticOrderTestUpdateDTO fixed = new DiagnosticOrderTestUpdateDTO(
                id,
                dto.patientId(),
                dto.encounterId(),
                dto.status(),
                dto.orderId(),
                dto.testId(),
                dto.receivedDepartmentId(),
                dto.reason(),
                dto.notes(),
                dto.processingStatus(),
                dto.submitDate(),
                dto.acceptedDate(),
                dto.rejectedDate(),
                dto.patientArrivedDate(),
                dto.readyDate(),
                dto.approvedDate(),
                dto.orderType(),
                dto.acceptedBy(),
                dto.rejectedBy(),
                dto.rejectedReason(),
                dto.patientArrivedNoteRad(),
                dto.cancellationReason(),
                dto.fromDepartmentId(),
                dto.fromFacilityId(),
                dto.toFacilityId(),
                dto.isActive()
        );

        DiagnosticOrderTest updated = diagnosticOrderTestService.update(existing, fixed);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @GetMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrderTest not found with id " + id,
                        "diagnostic_order_tests",
                        "notfound"
                ));
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(existing));
    }

    // Pagination style like VisitDuration + status include/exclude
    // Examples:
    //  - include: /diagnostic-orders/1500/tests?status=NEW&page=0&size=20
    //  - exclude: /diagnostic-orders/1500/tests?excludeStatus=REJECTED&excludeStatus=CANCELLED&page=0&size=20
    @GetMapping("/diagnostic-orders/{orderId}/tests")
    public ResponseEntity<List<DiagnosticOrderTestResponseVM>> getByOrderId(
            @PathVariable Long orderId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "excludeStatus", required = false) List<String> excludeStatus,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list DiagnosticOrderTests orderId={} status={} excludeStatus={} pageable={}",
                orderId, status, excludeStatus, pageable
        );

        Page<DiagnosticOrderTest> page = diagnosticOrderTestService.findByOrderIdFilterStatus(
                orderId, status, excludeStatus, pageable
        );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrderTest not found with id " + id,
                        "diagnostic_order_tests",
                        "notfound"
                ));
        diagnosticOrderTestService.delete(existing.getId());
        return ResponseEntity.noContent().build();
    }
}
