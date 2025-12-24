// src/main/java/com/dazzle/asklepios/web/rest/DiagnosticOrderController.java
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.service.DiagnosticOrderService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderResponseVM;
import org.springdoc.core.annotations.ParameterObject;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderController.class);

    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;

    public DiagnosticOrderController(DiagnosticOrderService diagnosticOrderService,
                                     DiagnosticOrderRepository diagnosticOrderRepository) {
        this.diagnosticOrderService = diagnosticOrderService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
    }

    @PostMapping("/diagnostic-orders")
    public ResponseEntity<DiagnosticOrderResponseVM> create(@Valid @RequestBody DiagnosticOrderCreateDTO dto) {
        LOG.debug("REST create DiagnosticOrder payload={}", dto);

        DiagnosticOrder saved = diagnosticOrderService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-orders/" + saved.getId()))
                .body(DiagnosticOrderResponseVM.ofEntity(saved));
    }

    @PutMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> update(@PathVariable Long id, @Valid @RequestBody DiagnosticOrderUpdateDTO dto) {
        LOG.debug("REST update DiagnosticOrder id={} payload={}", id, dto);

        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrder not found with id " + id,
                        "diagnostic_orders",
                        "notfound"
                ));

        DiagnosticOrderUpdateDTO fixed = new DiagnosticOrderUpdateDTO(
                id,
                dto.patientId(),
                dto.encounterId(),
                dto.status(),
                dto.saveDraft(),
                dto.submittedBy(),
                dto.submittedDate(),
                dto.isUrgent(),
                dto.labStatus(),
                dto.radStatus()
        );

        DiagnosticOrder updated = diagnosticOrderService.update(existing, fixed);
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(updated));
    }

    @GetMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrder not found with id " + id,
                        "diagnostic_orders",
                        "notfound"
                ));
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(existing));
    }

    // Pagination style like VisitDuration
    @GetMapping("/patients/{patientId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list DiagnosticOrders by patientId={} status={} pageable={}", patientId, status, pageable);

        Page<DiagnosticOrder> page = diagnosticOrderService.findByPatient(patientId, status, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/patients/{patientId}/encounters/{encounterId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatientAndEncounter(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list DiagnosticOrders by patientId={} encounterId={} status={} pageable={}",
                patientId, encounterId, status, pageable
        );

        Page<DiagnosticOrder> page = diagnosticOrderService.findByPatientAndEncounter(patientId, encounterId, status, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/encounters/{encounterId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list DiagnosticOrders by encounterId={} status={} pageable={}", encounterId, status, pageable);

        Page<DiagnosticOrder> page = diagnosticOrderService.findByEncounter(encounterId, status, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-orders/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrder not found with id " + id,
                        "diagnostic_orders",
                        "notfound"
                ));
        diagnosticOrderService.delete(existing.getId());
        return ResponseEntity.noContent().build();
    }
}
