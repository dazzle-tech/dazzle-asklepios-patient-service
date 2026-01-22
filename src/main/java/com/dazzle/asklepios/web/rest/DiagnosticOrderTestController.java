package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.client.domain.DiagnosticTest;
import com.dazzle.asklepios.client.domain.DiagnosticTestLaboratory;
import com.dazzle.asklepios.client.domain.DiagnosticTestRadiology;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.ExternalTestRepository;
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
import jakarta.persistence.criteria.Root;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestController.class);

    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final ExternalTestRepository externalTestRepository;

    public DiagnosticOrderTestController(
            DiagnosticOrderTestService diagnosticOrderTestService,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            ExternalTestRepository externalTestRepository
    ) {
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.externalTestRepository = externalTestRepository;
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests",
                        "No authenticated user"
                ));
    }

    /**
     * IMPORTANT:
     * external_test.test_id -> diagnostic_order_tests.id
     * so we check by DiagnosticOrderTest.getId()
     */
    private DiagnosticOrderTestResponseVM toVm(DiagnosticOrderTest t) {
        boolean sent = externalTestRepository.existsByTestId(t.getId());
        return DiagnosticOrderTestResponseVM.ofEntity(t, sent);
    }

    private List<DiagnosticOrderTestResponseVM> toVmListWithBulkSentFlag(List<DiagnosticOrderTest> tests) {
        if (tests == null || tests.isEmpty()) return List.of();

        Set<Long> ids = tests.stream()
                .map(DiagnosticOrderTest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> sentIds = new HashSet<>(externalTestRepository.findExistingTestIds(ids));

        return tests.stream()
                .map(t -> DiagnosticOrderTestResponseVM.ofEntity(t, sentIds.contains(t.getId())))
                .toList();
    }

    @PostMapping("/diagnostic-order-tests")
    public ResponseEntity<DiagnosticOrderTestResponseVM> create(@Valid @RequestBody DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("REST create DiagnosticOrderTest payload={}", dto);

        if (diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndStatusNot(
                dto.orderId(),
                dto.testId(),
                DiagnosticOrderTestStatus.CANCELLED
        )) {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }

        DiagnosticOrderTest saved = diagnosticOrderTestService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests/" + saved.getId()))
                .body(toVm(saved));
    }

    @PutMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestUpdateDTO dto
    ) {
        LOG.debug("REST update DiagnosticOrderTest id={} payload={}", id, dto);

        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + id
                ));

        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("Path id and body id mismatch", "diagnostic_order_tests", "idmismatch");
        }

        if (diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndIdNotAndStatusNot(
                dto.orderId(),
                dto.testId(),
                id,
                DiagnosticOrderTestStatus.CANCELLED
        )) {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }

        DiagnosticOrderTest updated = diagnosticOrderTestService.update(existing, dto);
        return ResponseEntity.ok(toVm(updated));
    }

    @GetMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + id
                ));
        return ResponseEntity.ok(toVm(existing));
    }

    @DeleteMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + id
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

        List<DiagnosticOrderTestResponseVM> body = toVmListWithBulkSentFlag(page.getContent());

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

            @RequestParam(name = "category", required = false) Long category,
            @RequestParam(name = "testName", required = false) String testName,

            @RequestParam(name = "submitDateFrom", required = false) Instant submitDateFrom,
            @RequestParam(name = "submitDateTo", required = false) Instant submitDateTo,

            @ParameterObject Pageable pageable
    ) {
        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either status or statusIn, not both");
        }

        if (category != null && orderType == null) {
            throw new BadRequestAlertException(
                    "missing_order_type",
                    "diagnostic_order_tests",
                    "orderType is required when filtering by categoryId"
            );
        }

        boolean hasNameFilter = testName != null && !testName.isBlank();

        Specification<DiagnosticOrderTest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (encounterId != null) predicates.add(cb.equal(root.get("encounterId"), encounterId));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (testId != null) predicates.add(cb.equal(root.get("testId"), testId));

            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) predicates.add(root.get("status").in(statusIn));
            if (excludeStatus != null) predicates.add(cb.notEqual(root.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty()) {
                predicates.add(cb.not(root.get("status").in(statusNotIn)));
            }

            if (receivedDepartmentId != null)
                predicates.add(cb.equal(root.get("receivedDepartmentId"), receivedDepartmentId));
            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (orderType != null) predicates.add(cb.equal(root.get("orderType"), orderType));

            if (acceptedBy != null && !acceptedBy.isBlank())
                predicates.add(cb.equal(root.get("acceptedBy"), acceptedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));

            if (submitDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("submitDate"), submitDateFrom));
            if (submitDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("submitDate"), submitDateTo));

            if (hasNameFilter) {
                Root<DiagnosticTest> dt = query.from(DiagnosticTest.class);
                predicates.add(cb.equal(dt.get("id"), root.get("testId")));
                predicates.add(cb.like(cb.lower(dt.get("name")), "%" + testName.toLowerCase() + "%"));
                query.distinct(true);
            }

            if (category != null) {
                if (orderType == TestType.LABORATORY) {
                    Root<DiagnosticTestLaboratory> lab = query.from(DiagnosticTestLaboratory.class);
                    predicates.add(cb.equal(lab.get("test").get("id"), root.get("testId")));
                    predicates.add(cb.equal(lab.get("category"), category));
                    query.distinct(true);
                } else if (orderType == TestType.RADIOLOGY) {
                    Root<DiagnosticTestRadiology> rad = query.from(DiagnosticTestRadiology.class);
                    predicates.add(cb.equal(rad.get("test").get("id"), root.get("testId")));
                    predicates.add(cb.equal(rad.get("category"), category));
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTest> page = diagnosticOrderTestRepository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestResponseVM> body = toVmListWithBulkSentFlag(page.getContent());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // -------------------------
    // ACTIONS (status changes)
    // -------------------------
    @PostMapping("/diagnostic-order-tests/{id}/collect-sample")
    public ResponseEntity<DiagnosticOrderTestResponseVM> collectSample(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.collectSample(id);
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/accept")
    public ResponseEntity<DiagnosticOrderTestResponseVM> accept(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.accept(id, username);
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/mark-ready")
    public ResponseEntity<DiagnosticOrderTestResponseVM> markReady(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.markReady(id);
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/review")
    public ResponseEntity<DiagnosticOrderTestResponseVM> review(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.review(id /*, username */);
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResponseVM> approve(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.approve(id);
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestRejectDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.reject(id, username, dto.rejectedReason());
        return ResponseEntity.ok(toVm(updated));
    }

    @PostMapping("/diagnostic-order-tests/{id}/cancel")
    public ResponseEntity<DiagnosticOrderTestResponseVM> cancel(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestCancelDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.cancel(id, username, dto.cancellationReason());
        return ResponseEntity.ok(toVm(updated));
    }
}
