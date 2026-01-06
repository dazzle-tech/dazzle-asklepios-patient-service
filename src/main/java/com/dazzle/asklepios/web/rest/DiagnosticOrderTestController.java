
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestService;
import com.dazzle.asklepios.service.DiagnosticOrderTestStatusService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.commands.DiagnosticOrderTestCancelDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.commands.DiagnosticOrderTestRejectDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestResponseVM;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestController.class);

    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticOrderTestController(
            DiagnosticOrderTestService diagnosticOrderTestService,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository
    ) {
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }


    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "No authenticated user",
                        "diagnostic_order_tests",
                        "unauthenticated"
                ));
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
    public ResponseEntity<DiagnosticOrderTestResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestUpdateDTO dto
    ) {
        LOG.debug("REST update DiagnosticOrderTest id={} payload={}", id, dto);

        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrderTest not found with id " + id,
                        "diagnostic_order_tests",
                        "notfound"
                ));

        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("Path id and body id mismatch", "diagnostic_order_tests", "idmismatch");
        }

        DiagnosticOrderTest updated = diagnosticOrderTestService.update(existing, dto);
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

    // -------------------------------------------------------
    // List tests by orderId (status include/exclude) - pagination
    // -------------------------------------------------------
    @GetMapping("/diagnostic-orders/{orderId}/tests")
    public ResponseEntity<List<DiagnosticOrderTestResponseVM>> getByOrderId(
            @PathVariable Long orderId,
            @RequestParam(name = "status", required = false) DiagnosticOrderTestStatus status,
            @RequestParam(name = "excludeStatus", required = false) List<DiagnosticOrderTestStatus> excludeStatus,
            @ParameterObject Pageable pageable
    ) {
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

    // -------------------------
    // FILTER (exact matching)
    // -------------------------
    @GetMapping("/diagnostic-order-tests")
    public ResponseEntity<List<DiagnosticOrderTestResponseVM>> filterDiagnosticOrderTests(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "testId", required = false) Long testId,

            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @RequestParam(name = "statusIn", required = false) List<DiagnosticStatus> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<DiagnosticStatus> statusNotIn,
            @RequestParam(name = "excludeStatus", required = false) DiagnosticStatus excludeStatus,

            @RequestParam(name = "receivedDepartmentId", required = false) Long receivedDepartmentId,

            @RequestParam(name = "processingStatus", required = false) DiagnosticStatus processingStatus,


            @RequestParam(name = "orderType", required = false) TestType orderType,

            @RequestParam(name = "acceptedBy", required = false) String acceptedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,

            @RequestParam(name = "fromDepartmentId", required = false) Long fromDepartmentId,
            @RequestParam(name = "fromFacilityId", required = false) Long fromFacilityId,
            @RequestParam(name = "toFacilityId", required = false) Long toFacilityId,

            @RequestParam(name = "submitDateFrom", required = false) Instant submitDateFrom,
            @RequestParam(name = "submitDateTo", required = false) Instant submitDateTo,

            @ParameterObject Pageable pageable
    ) {
        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either status or statusIn, not both");
        }

        Specification<DiagnosticOrderTest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (encounterId != null) predicates.add(cb.equal(root.get("encounterId"), encounterId));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (testId != null) predicates.add(cb.equal(root.get("testId"), testId));

            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) predicates.add(root.get("status").in(statusIn));
            if (excludeStatus != null) predicates.add(cb.notEqual(root.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty())
                predicates.add(cb.not(root.get("status").in(statusNotIn)));

            if (receivedDepartmentId != null)
                predicates.add(cb.equal(root.get("receivedDepartmentId"), receivedDepartmentId));
            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (orderType != null) predicates.add(cb.equal(root.get("orderType"), orderType));

            if (acceptedBy != null && !acceptedBy.isBlank())
                predicates.add(cb.equal(root.get("acceptedBy"), acceptedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));

            if (fromDepartmentId != null) predicates.add(cb.equal(root.get("fromDepartmentId"), fromDepartmentId));
            if (fromFacilityId != null) predicates.add(cb.equal(root.get("fromFacilityId"), fromFacilityId));
            if (toFacilityId != null) predicates.add(cb.equal(root.get("toFacilityId"), toFacilityId));

            if (submitDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("submitDate"), submitDateFrom));
            if (submitDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("submitDate"), submitDateTo));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTest> page = diagnosticOrderTestRepository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // -------------------------
    // ACTIONS (تغيير الحالة)
    // -------------------------
    @PostMapping("/diagnostic-order-tests/{id}/collect-sample")
    public ResponseEntity<DiagnosticOrderTestResponseVM> collectSample(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.collectSample(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/accept")
    public ResponseEntity<DiagnosticOrderTestResponseVM> accept(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.accept(id, username);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/mark-ready")
    public ResponseEntity<DiagnosticOrderTestResponseVM> markReady(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.markReady(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/review")
    public ResponseEntity<DiagnosticOrderTestResponseVM> review(@PathVariable Long id) {
        String username = currentUsername();

        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.review(id /*, username */);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResponseVM> approve(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.approve(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestRejectDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.reject(id, username, dto.rejectedReason());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/cancel")
    public ResponseEntity<DiagnosticOrderTestResponseVM> cancel(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestCancelDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.cancel(id, username, dto.cancellationReason());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

}
