package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.client.domain.DiagnosticTest;
import com.dazzle.asklepios.client.domain.DiagnosticTestLaboratory;
import com.dazzle.asklepios.client.domain.DiagnosticTestRadiology;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestService;
import com.dazzle.asklepios.service.DiagnosticOrderTestStatusService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.BulkIdsDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.BulkRejectDTO;
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

/**
 * REST controller for managing {@link DiagnosticOrderTest} resources.
 * <p>
 * Endpoints provided:
 * <ul>
 *   <li>CRUD operations on diagnostic order tests</li>
 *   <li>Listing tests for a specific order with include/exclude status filters + pagination</li>
 *   <li>Advanced filtering using query params (Specification-based)</li>
 *   <li>Status/action endpoints (collect sample, accept, mark ready, review, approve, reject, cancel)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestController {

    /**
     * Logger for tracing incoming REST requests.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestController.class);

    /**
     * Service handling create/update/delete and list operations.
     */
    private final DiagnosticOrderTestService diagnosticOrderTestService;

    /**
     * Service handling state transitions for processing/status actions.
     */
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    /**
     * Repository used directly for lookups and Specification-based queries.
     */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestTechnicianNoteRepository diagnosticOrderTestTechnicianNoteRepository;
    /**
     * Controller constructor.
     *
     * @param diagnosticOrderTestService       business logic for CRUD
     * @param diagnosticOrderTestStatusService business logic for status transitions
     * @param diagnosticOrderTestRepository    persistence/retrieval access
     */
    public DiagnosticOrderTestController(
            DiagnosticOrderTestService diagnosticOrderTestService,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository, DiagnosticOrderTestTechnicianNoteRepository diagnosticOrderTestTechnicianNoteRepository
    ) {
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderTestTechnicianNoteRepository = diagnosticOrderTestTechnicianNoteRepository;
    }

    /**
     * Returns the current authenticated username (login).
     *
     * @return username of the authenticated user
     * @throws BadRequestAlertException if no user is authenticated
     */
    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests",
                        "No authenticated user"
                ));
    }

    /**
     * Creates a new DiagnosticOrderTest.
     *
     * @param dto request body payload
     * @return created entity response with Location header
     */
    @PostMapping("/diagnostic-order-tests")
    public ResponseEntity<DiagnosticOrderTestResponseVM> create(@Valid @RequestBody DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("REST create DiagnosticOrderTest payload={}", dto);
        if (diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndStatusNot(dto.orderId(), dto.testId(), DiagnosticOrderTestStatus.CANCELLED))
        {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }

        DiagnosticOrderTest saved = diagnosticOrderTestService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests/" + saved.getId()))
                .body(DiagnosticOrderTestResponseVM.ofEntity(saved));
    }

    /**
     * Updates an existing DiagnosticOrderTest by id.
     * <p>
     * Validates that the path id matches the DTO id to avoid accidental updates.
     *
     * @param id  path variable (entity id)
     * @param dto request body payload (must include matching id)
     * @return updated entity response
     */
    @PutMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestUpdateDTO dto
    ) {
        LOG.debug("REST update DiagnosticOrderTest id={} payload={}", id, dto);

        // Load existing entity or fail fast
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + id
                ));

        // Guard against path/body id mismatch
        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("Path id and body id mismatch", "diagnostic_order_tests", "idmismatch");
        }
        if (
                diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndIdNotAndStatusNot(
                        dto.orderId(),
                        dto.testId(),
                        id
                        , DiagnosticOrderTestStatus.CANCELLED
                )
        ) {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }


        DiagnosticOrderTest updated = diagnosticOrderTestService.update(existing, dto);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Retrieves a DiagnosticOrderTest by its id.
     *
     * @param id entity id
     * @return entity response
     */
    @GetMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTest existing = diagnosticOrderTestRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + id
                ));
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntityWithNote(existing,diagnosticOrderTestTechnicianNoteRepository.existsByOrderTestId(existing.getTestId())));
    }

    /**
     * Deletes a DiagnosticOrderTest by its id.
     *
     * @param id entity id
     * @return 204 No Content if deleted
     */
    @DeleteMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Ensure the entity exists before deleting (for consistent error handling)
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

    /**
     * Lists tests under a specific diagnostic order id with optional include/exclude filters.
     * <p>
     * Supports:
     * <ul>
     *   <li>{@code status}: include only this {@link DiagnosticOrderTestStatus}</li>
     *   <li>{@code excludeStatus}: exclude these statuses</li>
     *   <li>Pagination via {@link Pageable}</li>
     * </ul>
     *
     * @param orderId       parent order id
     * @param status        optional exact status filter
     * @param excludeStatus optional list of statuses to exclude (used when status is null)
     * @param pageable      paging and sorting
     * @return paginated list of response VMs plus pagination headers
     */
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

        // Attach standard pagination headers (Link + X-Total-Count style)
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        // Map entities to response view-models
        List<DiagnosticOrderTestResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // -------------------------
    // FILTER (exact matching)
    // -------------------------

    /**
     * Filters DiagnosticOrderTest records using query parameters.
     * <p>
     * Implementation uses JPA {@link Specification} for exact-match predicates (plus submitDate range).
     * Results are pageable.
     * <p>
     * Notes/constraints:
     * <ul>
     *   <li>Cannot use both {@code status} and {@code statusIn} at the same time.</li>
     *   <li>{@code submitDateFrom}/{@code submitDateTo} apply inclusive bounds.</li>
     * </ul>
     *
     * @return paginated list of matching tests plus pagination headers
     */
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

            // independent filters
            @RequestParam(name = "category", required = false) Long category,
            @RequestParam(name = "testName", required = false) String testName,

            @RequestParam(name = "submitDateFrom", required = false) Instant submitDateFrom,
            @RequestParam(name = "submitDateTo", required = false) Instant submitDateTo,

            @ParameterObject Pageable pageable
    ) {
        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either status or statusIn, not both");
        }

        // categoryId requires orderType (because category lives in different detail tables)
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

            // Core identifiers
            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (encounterId != null) predicates.add(cb.equal(root.get("encounterId"), encounterId));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (testId != null) predicates.add(cb.equal(root.get("testId"), testId));

            // Status filtering
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) predicates.add(root.get("status").in(statusIn));
            if (excludeStatus != null) predicates.add(cb.notEqual(root.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty()) {
                predicates.add(cb.not(root.get("status").in(statusNotIn)));
            }

            // Department & processing details
            if (receivedDepartmentId != null)
                predicates.add(cb.equal(root.get("receivedDepartmentId"), receivedDepartmentId));
            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (orderType != null) predicates.add(cb.equal(root.get("orderType"), orderType));

            // Audit fields
            if (acceptedBy != null && !acceptedBy.isBlank())
                predicates.add(cb.equal(root.get("acceptedBy"), acceptedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));

            // Submit date range
            if (submitDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("submitDate"), submitDateFrom));
            if (submitDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("submitDate"), submitDateTo));

            // -----------------------------
            // (1) testName filter (ONLY from diagnostic_test)
            // Does NOT depend on categoryId or orderType
            // -----------------------------
            if (hasNameFilter) {
                Root<DiagnosticTest> dt = query.from(DiagnosticTest.class);
                predicates.add(cb.equal(dt.get("id"), root.get("testId")));
                predicates.add(cb.like(
                        cb.lower(dt.get("name")),
                        "%" + testName.toLowerCase() + "%"
                ));
                query.distinct(true);
            }

            // -----------------------------
            // (2) categoryId filter (ONLY from detail table based on orderType)
            // Does NOT depend on testName
            // -----------------------------
            if (category != null) {
                if (orderType == TestType.LABORATORY) {
                    Root<DiagnosticTestLaboratory> lab = query.from(DiagnosticTestLaboratory.class);

                    // Link: DiagnosticOrderTest.testId -> DiagnosticTestLaboratory.test.id
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

        List<DiagnosticOrderTestResponseVM> body = page.getContent()
                .stream()
                .map(t -> DiagnosticOrderTestResponseVM.ofEntityWithNote(
                        t,
                        diagnosticOrderTestTechnicianNoteRepository.existsByOrderTestId(t.getId())
                ))
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    // -------------------------
    // ACTIONS (status changes)
    // -------------------------

    /**
     * Action endpoint: collect sample for a test (processingStatus transition).
     *
     * @param id test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/collect-sample")
    public ResponseEntity<DiagnosticOrderTestResponseVM> collectSample(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.collectSample(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: accept a test (processingStatus transition).
     * <p>
     * Uses the currently authenticated username as the accepter.
     *
     * @param id test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/accept")
    public ResponseEntity<DiagnosticOrderTestResponseVM> accept(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.accept(id, username);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: mark a test as ready (processingStatus transition).
     *
     * @param id test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/mark-ready")
    public ResponseEntity<DiagnosticOrderTestResponseVM> markReady(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.markReady(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: review a test (optional processingStatus transition).
     *
     * @param id test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/review")
    public ResponseEntity<DiagnosticOrderTestResponseVM> review(@PathVariable Long id) {
        // Username retrieved but not currently used in service call (kept for future audit support)
        String username = currentUsername();

        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.review(id /*, username */);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: approve a test result (processingStatus transition).
     *
     * @param id test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResponseVM> approve(@PathVariable Long id) {
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.approve(id);
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: reject a test.
     * <p>
     * Uses the currently authenticated username as the rejecter.
     *
     * @param id  test id
     * @param dto rejection payload (reason)
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestRejectDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.reject(id, username, dto.rejectedReason());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Action endpoint: cancel a test.
     * <p>
     * Uses the currently authenticated username as the canceller.
     *
     * @param id  test id
     * @param dto cancellation payload (reason)
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/cancel")
    public ResponseEntity<DiagnosticOrderTestResponseVM> cancel(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestCancelDTO dto
    ) {
        String username = currentUsername();
        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.cancel(id, username, dto.cancellationReason());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }

    /**
     * Bulk action: accept multiple tests.
     * <p>
     * Applies the same workflow as {@code /diagnostic-order-tests/{id}/accept} but for a list of ids.
     *
     * @param dto list of test ids to accept
     * @return 200 OK on success
     */
    @PostMapping("/diagnostic-order-tests/bulk-accept")
    public ResponseEntity<Void> bulkAccept(@Valid @RequestBody BulkIdsDTO dto) {
        LOG.debug("REST bulk-accept DiagnosticOrderTest count={} ids={}", dto.ids().size(), dto.ids());
        diagnosticOrderTestStatusService.bulkAccept(dto.ids(), currentUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * Bulk action: reject multiple tests.
     * <p>
     * Applies the same workflow as {@code /diagnostic-order-tests/{id}/reject} but for a list of ids.
     *
     * @param dto list of test ids + rejection reason
     * @return 200 OK on success
     */
    @PostMapping("/diagnostic-order-tests/bulk-reject")
    public ResponseEntity<Void> bulkReject(@Valid @RequestBody BulkRejectDTO dto) {
        LOG.debug("REST bulk-reject DiagnosticOrderTest count={} ids={} reason={}",
                dto.ids().size(), dto.ids(), dto.rejectedReason());
        diagnosticOrderTestStatusService.bulkReject(dto.ids(), currentUsername(), dto.rejectedReason());
        return ResponseEntity.ok().build();
    }


    /**
     * Action endpoint: undo accept for a test.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Deletes all {@code DiagnosticOrderTestResult} records for this test.</li>
     *   <li>Resets {@code processingStatus} back to {@link DiagnosticStatus#NEW}.</li>
     *   <li>Clears acceptance/ready/approval/rejection audit fields on the test.</li>
     *   <li>Recomputes parent order lab/rad aggregated statuses.</li>
     * </ul>
     *
     * @param id DiagnosticOrderTest id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/undo-accept")
    public ResponseEntity<DiagnosticOrderTestResponseVM> undoAccept(@PathVariable Long id) {
        LOG.debug("REST undo-accept DiagnosticOrderTest id={}", id);

        DiagnosticOrderTest updated = diagnosticOrderTestStatusService.undoAccept(id);

        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updated));
    }


}
