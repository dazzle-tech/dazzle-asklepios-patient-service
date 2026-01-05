
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestResponseVM;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// Add these imports

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;

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



    /**
     * {@code GET /diagnostic-order-tests} : Filter diagnostic order tests (exact matching, not a text search).
     *
     * <p>
     * Returns a paginated list of {@link DiagnosticOrderTest} records using optional query parameters.
     * Filters are applied with exact semantics ({@code =}, {@code IN}, {@code NOT IN}) and optional date ranges.
     * </p>
     *
     * @param patientId optional patient identifier to scope results.
     * @param encounterId optional encounter identifier to scope results.
     * @param orderId optional diagnostic order identifier to scope results.
     * @param testId optional test identifier to scope results.
     * @param status optional exact status filter (mutually exclusive with {@code statusIn}).
     * @param statusIn optional list of statuses to include.
     * @param statusNotIn optional list of statuses to exclude.
     * @param excludeStatus optional single status to exclude (convenience).
     * @param processingStatus optional exact processing status filter.
     * @param orderType optional exact order type filter.
     * @param receivedDepartmentId optional exact received department filter.
     * @param fromDepartmentId optional exact from department filter.
     * @param fromFacilityId optional exact from facility filter.
     * @param toFacilityId optional exact to facility filter.
     * @param acceptedBy optional exact accepted by filter.
     * @param rejectedBy optional exact rejected by filter.
     * @param submitDateFrom optional submitDate lower bound (inclusive).
     * @param submitDateTo optional submitDate upper bound (inclusive).
     * @param pageable pagination and sorting information.
     * @return {@code 200 (OK)} with filtered results and pagination headers.
     *         {@code 400 (Bad Request)} if conflicting filters are provided.
     */
    @GetMapping("/diagnostic-order-tests")
    public ResponseEntity<List<DiagnosticOrderTestResponseVM>> filterDiagnosticOrderTests(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "testId", required = false) Long testId,

            // Status filters
            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @RequestParam(name = "statusIn", required = false) List<DiagnosticStatus> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<DiagnosticStatus> statusNotIn,
            @RequestParam(name = "excludeStatus", required = false) DiagnosticStatus excludeStatus,

            // Other exact filters
            @RequestParam(name = "receivedDepartmentId", required = false) Long receivedDepartmentId,
            @RequestParam(name = "processingStatus", required = false) String processingStatus,
            @RequestParam(name = "orderType", required = false) String orderType,
            @RequestParam(name = "acceptedBy", required = false) String acceptedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,

            @RequestParam(name = "fromDepartmentId", required = false) Long fromDepartmentId,
            @RequestParam(name = "fromFacilityId", required = false) Long fromFacilityId,
            @RequestParam(name = "toFacilityId", required = false) Long toFacilityId,

            // Date range (example: submit_date)
            @RequestParam(name = "submitDateFrom", required = false) Instant submitDateFrom,
            @RequestParam(name = "submitDateTo", required = false) Instant submitDateTo,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTest] FILTER - request received. patientId={} encounterId={} orderId={} testId={} " +
                        "status={} statusIn={} statusNotIn={} excludeStatus={} receivedDepartmentId={} processingStatus={} orderType={} " +
                        "acceptedBy={} rejectedBy={} fromDepartmentId={} fromFacilityId={} toFacilityId={} submitDateFrom={} submitDateTo={} pageable={}",
                patientId, encounterId, orderId, testId,
                status, statusIn, statusNotIn, excludeStatus,
                receivedDepartmentId, processingStatus, orderType,
                acceptedBy, rejectedBy,
                fromDepartmentId, fromFacilityId, toFacilityId,
                submitDateFrom, submitDateTo, pageable
        );

        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            LOG.warn("[DiagnosticOrderTest] FILTER - invalid request: status and statusIn were both provided. status={} statusIn={}", status, statusIn);
            throw new IllegalArgumentException("Use either status or statusIn, not both");
        }

        Specification<DiagnosticOrderTest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Scope filters
            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (encounterId != null) predicates.add(cb.equal(root.get("encounterId"), encounterId));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (testId != null) predicates.add(cb.equal(root.get("testId"), testId));

            // Status filters
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) predicates.add(root.get("status").in(statusIn));
            if (excludeStatus != null) predicates.add(cb.notEqual(root.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty()) predicates.add(cb.not(root.get("status").in(statusNotIn)));

            // Other exact filters
            if (receivedDepartmentId != null) predicates.add(cb.equal(root.get("receivedDepartmentId"), receivedDepartmentId));
            if (processingStatus != null && !processingStatus.isBlank()) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (orderType != null && !orderType.isBlank()) predicates.add(cb.equal(root.get("orderType"), orderType));
            if (acceptedBy != null && !acceptedBy.isBlank()) predicates.add(cb.equal(root.get("acceptedBy"), acceptedBy));
            if (rejectedBy != null && !rejectedBy.isBlank()) predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));

            if (fromDepartmentId != null) predicates.add(cb.equal(root.get("fromDepartmentId"), fromDepartmentId));
            if (fromFacilityId != null) predicates.add(cb.equal(root.get("fromFacilityId"), fromFacilityId));
            if (toFacilityId != null) predicates.add(cb.equal(root.get("toFacilityId"), toFacilityId));

            // Date range
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

        LOG.debug("[DiagnosticOrderTest] FILTER - response ready. returned={} totalElements={} totalPages={} pageNumber={} pageSize={}",
                body.size(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

}
