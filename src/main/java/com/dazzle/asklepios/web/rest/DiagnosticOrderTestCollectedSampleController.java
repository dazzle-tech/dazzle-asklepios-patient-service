package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import com.dazzle.asklepios.repository.DiagnosticOrderTestCollectedSampleRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestCollectedSampleService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkSameDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestCollectedSampleController.class);

    private final DiagnosticOrderTestCollectedSampleService service;
    private final DiagnosticOrderTestCollectedSampleRepository repository;
    private final DiagnosticOrderTestRepository orderTestRepository;

    public DiagnosticOrderTestCollectedSampleController(
            DiagnosticOrderTestCollectedSampleService service,
            DiagnosticOrderTestCollectedSampleRepository repository,
            DiagnosticOrderTestRepository orderTestRepository
    ) {
        this.service = service;
        this.repository = repository;
        this.orderTestRepository = orderTestRepository;
    }

    @PostMapping("/diagnostic-order-test-collected-samples")
    public ResponseEntity<DiagnosticOrderTestCollectedSample> create(
            @Valid @RequestBody DiagnosticOrderTestCollectedSampleDTO dto
    ) {
        LOG.debug("[CollectedSample] CREATE - request received. payload={}", dto);
        DiagnosticOrderTest test = orderTestRepository.findById(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + dto.orderTestId()
                ));

        if (test.getOrderId() == null || !test.getOrderId().equals(dto.orderId())) {
            throw new BadRequestAlertException(
                    "order_mismatch",
                    "diagnostic_order_test_collected_samples",
                    "orderId does not match the order of the given orderTestId"
            );
        }

        DiagnosticOrderTestCollectedSample saved = service.create(dto);

        LOG.debug("[CollectedSample] CREATE - created successfully. id={}", saved.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-collected-samples/" + saved.getId()))
                .body(saved);
    }

    @GetMapping("/diagnostic-order-test-collected-samples/{id}")
    public ResponseEntity<DiagnosticOrderTestCollectedSample> getById(@PathVariable Long id) {
        LOG.debug("[CollectedSample] GET_BY_ID - request received. id={}", id);
        DiagnosticOrderTestCollectedSample existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_collected_samples",
                        "Collected sample not found with id " + id
                ));
        LOG.debug("[CollectedSample] GET_BY_ID - found. id={}", existing.getId());
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/diagnostic-order-test-collected-samples/by-diagnostic-order-tests/{orderTestId}")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSample>> listByOrderTestId(
            @PathVariable Long orderTestId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[CollectedSample] LIST_BY_ORDER_TEST - request received. orderTestId={} pageable={}", orderTestId, pageable);
        Page<DiagnosticOrderTestCollectedSample> page = repository.findByOrderTestId(orderTestId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        LOG.debug("[CollectedSample] LIST_BY_ORDER_TEST - response ready. orderTestId={} returned={} totalElements={} totalPages={}",
                orderTestId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/diagnostic-order-test-collected-samples/by-diagnostic-orders/{orderId}")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSample>> listByOrderId(
            @PathVariable Long orderId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[CollectedSample] LIST_BY_ORDER - request received. orderId={} pageable={}", orderId, pageable);
        Page<DiagnosticOrderTestCollectedSample> page = repository.findByOrderId(orderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        LOG.debug("[CollectedSample] LIST_BY_ORDER - response ready. orderId={} returned={} totalElements={} totalPages={}",
                orderId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-order-test-collected-samples/{id}")
    public ResponseEntity<Void> delete(@Valid @PathVariable Long id) {
        LOG.debug("[CollectedSample] DELETE - request received. id={}", id);
        DiagnosticOrderTestCollectedSample existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_collected_samples",
                        "Collected sample not found with id " + id
                ));

        service.delete(existing.getId());
        LOG.debug("[CollectedSample] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/diagnostic-order-test-collected-samples/bulk-with-same-details")
    public ResponseEntity<List<DiagnosticOrderTestCollectedSample>> bulkCreateWithSameDetails(
            @Valid @RequestBody DiagnosticOrderTestCollectedSampleBulkSameDTO dto
    ) {
        LOG.debug("[CollectedSample] BULK_CREATE_SAME - request received. orderId={} orderTestIdsCount={}",
                dto.orderId(), dto.orderTestIds() == null ? 0 : dto.orderTestIds().size());
        for (Long orderTestId : dto.orderTestIds()) {
            DiagnosticOrderTest orderTest = orderTestRepository.findById(orderTestId)
                    .orElseThrow(() -> new BadRequestAlertException(
                            "notfound",
                            "diagnostic_order_tests",
                            "DiagnosticOrderTest not found with id " + orderTestId
                    ));

            if (orderTest.getOrderId() == null || !orderTest.getOrderId().equals(dto.orderId())) {
                throw new BadRequestAlertException(
                        "order_mismatch",
                        "diagnostic_order_test_collected_samples",
                        "orderId does not match the order of orderTestId " + orderTestId
                );
            }
        }

        List<DiagnosticOrderTestCollectedSample> saved = service.bulkCreateWithSameDetails(dto);
        LOG.debug("[CollectedSample] BULK_CREATE_SAME - created successfully. savedCount={}", saved.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
