package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import com.dazzle.asklepios.repository.DiagnosticOrderTestCollectedSampleRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestCollectedSampleService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkSameDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.collectedsample.DiagnosticOrderTestCollectedSampleResponseVM;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestCollectedSampleController {

    private final DiagnosticOrderTestCollectedSampleService service;
    private final DiagnosticOrderTestCollectedSampleRepository repository;
    private final DiagnosticOrderTestRepository orderTestRepository;

    public DiagnosticOrderTestCollectedSampleController(DiagnosticOrderTestCollectedSampleService service, DiagnosticOrderTestCollectedSampleRepository repository, DiagnosticOrderTestRepository orderTestRepository) {
        this.service = service;
        this.repository = repository;
        this.orderTestRepository = orderTestRepository;
    }


    @PostMapping("/diagnostic-order-test-collected-samples")
    public ResponseEntity<DiagnosticOrderTestCollectedSampleResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestCollectedSampleDTO dto
    ) {
        // Validate order_test exists
        DiagnosticOrderTest test = orderTestRepository.findById(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + dto.orderTestId()
                ));

        // Validate order_id matches test.orderId
        if (test.getOrderId() == null || !test.getOrderId().equals(dto.orderId())) {
            throw new BadRequestAlertException(
                    "order_mismatch",
                    "diagnostic_order_test_collected_samples",
                    "orderId does not match the order of the given orderTestId"
            );
        }

        DiagnosticOrderTestCollectedSample saved = service.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-collected-samples/" + saved.getId()))
                .body(DiagnosticOrderTestCollectedSampleResponseVM.ofEntity(saved));
    }

    @GetMapping("/diagnostic-order-test-collected-samples/{id}")
    public ResponseEntity<DiagnosticOrderTestCollectedSampleResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTestCollectedSample existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_collected_samples",
                        "Collected sample not found with id " + id
                ));
        return ResponseEntity.ok(DiagnosticOrderTestCollectedSampleResponseVM.ofEntity(existing));
    }

    @GetMapping("/diagnostic-order-tests/{orderTestId}/collected-samples")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSampleResponseVM>> listByOrderTestId(
            @PathVariable Long orderTestId,
            @ParameterObject Pageable pageable
    ) {
        Page<DiagnosticOrderTestCollectedSample> page = repository.findByOrderTestId(orderTestId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestCollectedSampleResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestCollectedSampleResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/diagnostic-orders/{orderId}/collected-samples")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSampleResponseVM>> listByOrderId(
            @PathVariable Long orderId,
            @ParameterObject Pageable pageable
    ) {
        Page<DiagnosticOrderTestCollectedSample> page = repository.findByOrderId(orderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestCollectedSampleResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestCollectedSampleResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-order-test-collected-samples/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrderTestCollectedSample existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_collected_samples",
                        "Collected sample not found with id " + id
                ));

        service.delete(existing.getId());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/diagnostic-order-test-collected-samples/bulk-same")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSampleResponseVM>> bulkCreateSame(
            @Valid @RequestBody DiagnosticOrderTestCollectedSampleBulkSameDTO dto
    ) {
        // Validate all orderTestIds exist and belong to the same orderId
        for (Long orderTestId : dto.orderTestIds()) {
            DiagnosticOrderTest test = orderTestRepository.findById(orderTestId)
                    .orElseThrow(() -> new BadRequestAlertException(
                            "notfound",
                            "diagnostic_order_tests",
                            "DiagnosticOrderTest not found with id " + orderTestId
                    ));

            if (test.getOrderId() == null || !test.getOrderId().equals(dto.orderId())) {
                throw new BadRequestAlertException(
                        "order_mismatch",
                        "diagnostic_order_test_collected_samples",
                        "orderId does not match the order of orderTestId " + orderTestId
                );
            }
        }

        List<DiagnosticOrderTestCollectedSample> saved = service.bulkCreateSame(dto);

        List<DiagnosticOrderTestCollectedSampleResponseVM> body = saved.stream()
                .map(DiagnosticOrderTestCollectedSampleResponseVM::ofEntity)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }


}
