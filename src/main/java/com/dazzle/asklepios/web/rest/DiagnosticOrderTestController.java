package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticTest;
import com.dazzle.asklepios.domain.DiagnosticTestLaboratory;
import com.dazzle.asklepios.domain.DiagnosticTestRadiology;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
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
            DiagnosticOrderTestRepository diagnosticOrderTestRepository
    ) {
        this.diagnosticOrderTestService = diagnosticOrderTestService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    /**
     * Returns the current authenticated username (login).
     *
     * @return username of the authenticated user
     * @throws BadRequestAlertException if no user is authenticated
     */
    private String currentUsername() {
        LOG.debug("[DiagnosticOrderTest] CURRENT_USER - resolving username");
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
     * @param requestDto request body payload
     * @return created entity response with Location header
     */
    @PostMapping("/diagnostic-order-tests")
    public ResponseEntity<DiagnosticOrderTestResponseVM> create(@Valid @RequestBody DiagnosticOrderTestCreateDTO requestDto) {
        LOG.debug("[DiagnosticOrderTest] CREATE - request received. payload={}", requestDto);
        if (diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndStatusNot(
                requestDto.orderId(),
                requestDto.testId(),
                DiagnosticOrderTestStatus.CANCELLED
        )) {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }

        DiagnosticOrderTest createdTest = diagnosticOrderTestService.create(requestDto);

        LOG.debug("[DiagnosticOrderTest] CREATE - created successfully. id={}", createdTest.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests/" + createdTest.getId()))
                .body(DiagnosticOrderTestResponseVM.ofEntity(createdTest));
    }

    /**
     * Updates an existing DiagnosticOrderTest by id.
     * <p>
     * Validates that the path id matches the DTO id to avoid accidental updates.
     *
     * @param requestDto  path variable (entity id)
     * @param orderTestId request body payload (must include matching id)
     * @return updated entity response
     */
    @PutMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> update(
            @PathVariable("id") Long orderTestId,
            @Valid @RequestBody DiagnosticOrderTestUpdateDTO requestDto
    ) {
        LOG.debug("[DiagnosticOrderTest] UPDATE - request received. id={} payload={}", orderTestId, requestDto);

        // Load existing entity or fail fast
        DiagnosticOrderTest existingTest = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        // Guard against path/body id mismatch
        if (!orderTestId.equals(requestDto.id())) {
            throw new BadRequestAlertException("Path id and body id mismatch", "diagnostic_order_tests", "idmismatch");
        }
        if (
                diagnosticOrderTestRepository.existsByOrderIdAndTestIdAndIdNotAndStatusNot(
                        requestDto.orderId(),
                        requestDto.testId(),
                        orderTestId
                        , DiagnosticOrderTestStatus.CANCELLED
                )
        ) {
            throw new BadRequestAlertException(
                    "duplicate_test_in_order",
                    "diagnostic_order_tests",
                    "This test already exists for the same order"
            );
        }


        DiagnosticOrderTest updatedTest = diagnosticOrderTestService.update(existingTest, requestDto);
        LOG.debug("[DiagnosticOrderTest] UPDATE - updated successfully. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Retrieves a DiagnosticOrderTest by its id.
     *
     * @param orderTestId entity id
     * @return entity response
     */
    @GetMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<DiagnosticOrderTestResponseVM> getById(@PathVariable("id") Long orderTestId) {
        LOG.debug("[DiagnosticOrderTest] GET_BY_ID - request received. id={}", orderTestId);
        DiagnosticOrderTest existingTest = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));
        LOG.debug("[DiagnosticOrderTest] GET_BY_ID - found. id={}", existingTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(existingTest));
    }

    /**
     * Deletes a DiagnosticOrderTest by its id.
     *
     * @param orderTestId entity id
     * @return 204 No Content if deleted
     */
    @DeleteMapping("/diagnostic-order-tests/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long orderTestId) {
        LOG.debug("[DiagnosticOrderTest] DELETE - request received. id={}", orderTestId);
        // Ensure the entity exists before deleting (for consistent error handling)
        DiagnosticOrderTest existingTest = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        diagnosticOrderTestService.delete(existingTest.getId());
        LOG.debug("[DiagnosticOrderTest] DELETE - deleted successfully. id={}", orderTestId);
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
     * @param orderId          parent order id
     * @param status           optional exact status filter
     * @param excludedStatuses optional list of statuses to exclude (used when status is null)
     * @param pageable         paging and sorting
     * @return paginated list of response VMs plus pagination headers
     */
    @GetMapping("/diagnostic-order-tests/by-order/{orderId}")
    public ResponseEntity<List<DiagnosticOrderTestResponseVM>> getByOrderId(
            @PathVariable Long orderId,
            @RequestParam(name = "status", required = false) DiagnosticOrderTestStatus status,
            @RequestParam(name = "excludeStatus", required = false) List<DiagnosticOrderTestStatus> excludedStatuses,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTest] LIST_BY_ORDER - request received. orderId={} status={} excludeStatus={} pageable={}",
                orderId, status, excludedStatuses, pageable);
        Page<DiagnosticOrderTest> orderTestsPage;
        if (status != null) {
            orderTestsPage = diagnosticOrderTestService.findByOrderIdAndStatus(orderId, status, pageable);
        } else if (excludedStatuses != null && !excludedStatuses.isEmpty()) {
            orderTestsPage = diagnosticOrderTestService.findByOrderIdExcludingStatuses(orderId, excludedStatuses, pageable);
        } else {
            orderTestsPage = diagnosticOrderTestService.findByOrderId(orderId, pageable);
        }

        // Attach standard pagination headers (Link + X-Total-Count style)
        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), orderTestsPage
        );

        // Map entities to response view-models
        List<DiagnosticOrderTestResponseVM> responseBody = orderTestsPage.getContent()
                .stream()
                .map(DiagnosticOrderTestResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrderTest] LIST_BY_ORDER - response ready. orderId={} returned={} totalElements={} totalPages={}",
                orderId, responseBody.size(), orderTestsPage.getTotalElements(), orderTestsPage.getTotalPages());
        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
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
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "testId", required = false) Long testId,

            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @RequestParam(name = "statusIn", required = false) List<DiagnosticStatus> includedStatuses,
            @RequestParam(name = "statusNotIn", required = false) List<DiagnosticStatus> excludedStatuses,
            @RequestParam(name = "excludeStatus", required = false) DiagnosticStatus excludedStatus,

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
        LOG.debug("[DiagnosticOrderTest] FILTER - request received.  orderId={} testId={} status={} statusIn={} statusNotIn={} excludeStatus={} receivedDepartmentId={} processingStatus={} orderType={} acceptedBy={} rejectedBy={} category={} testName={} submitDateFrom={} submitDateTo={} pageable={}",
                orderId, testId, status, includedStatuses, excludedStatuses, excludedStatus,

                receivedDepartmentId, processingStatus, orderType, acceptedBy, rejectedBy, category, testName,
                submitDateFrom, submitDateTo, pageable);
        if (status != null && includedStatuses != null && !includedStatuses.isEmpty()) {
            throw new BadRequestAlertException(
                    "invalid_filter",
                    "diagnostic_order_tests",
                    "Use either status or statusIn, not both"
            );
        }

        // categoryId requires orderType (because category lives in different detail tables)
        if (category != null && orderType == null) {
            throw new BadRequestAlertException(
                    "missing_order_type",
                    "diagnostic_order_tests",
                    "orderType is required when filtering by categoryId"
            );
        }

        boolean hasTestNameFilter = testName != null && !testName.isBlank();

        Specification<DiagnosticOrderTest> filterSpec = (orderTestRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> filterPredicates = new ArrayList<>();

            // Core identifiers
            if (orderId != null) filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("orderId"), orderId));
            if (testId != null) filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("testId"), testId));

            // Status filtering
            if (status != null) filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("status"), status));
            if (includedStatuses != null && !includedStatuses.isEmpty())
                filterPredicates.add(orderTestRoot.get("status").in(includedStatuses));
            if (excludedStatus != null)
                filterPredicates.add(criteriaBuilder.notEqual(orderTestRoot.get("status"), excludedStatus));
            if (excludedStatuses != null && !excludedStatuses.isEmpty()) {
                filterPredicates.add(criteriaBuilder.not(orderTestRoot.get("status").in(excludedStatuses)));
            }

            // Department & processing details
            if (receivedDepartmentId != null)
                filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("receivedDepartmentId"), receivedDepartmentId));
            if (processingStatus != null)
                filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("processingStatus"), processingStatus));
            if (orderType != null)
                filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("orderType"), orderType));

            // Audit fields
            if (acceptedBy != null && !acceptedBy.isBlank())
                filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("acceptedBy"), acceptedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                filterPredicates.add(criteriaBuilder.equal(orderTestRoot.get("rejectedBy"), rejectedBy));

            // Submit date range
            if (submitDateFrom != null)
                filterPredicates.add(criteriaBuilder.greaterThanOrEqualTo(orderTestRoot.get("submitDate"), submitDateFrom));
            if (submitDateTo != null)
                filterPredicates.add(criteriaBuilder.lessThanOrEqualTo(orderTestRoot.get("submitDate"), submitDateTo));

            // -----------------------------
            // (1) testName filter (ONLY from diagnostic_test)
            // Does NOT depend on categoryId or orderType
            // -----------------------------
            if (hasTestNameFilter) {
                Root<DiagnosticTest> testRoot = criteriaQuery.from(DiagnosticTest.class);
                filterPredicates.add(criteriaBuilder.equal(testRoot.get("id"), orderTestRoot.get("testId")));
                filterPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(testRoot.get("name")),
                        "%" + testName.toLowerCase() + "%"
                ));
                criteriaQuery.distinct(true);
            }

            // -----------------------------
            // (2) categoryId filter (ONLY from detail table based on orderType)
            // Does NOT depend on testName
            // -----------------------------
            if (category != null) {
                if (orderType == TestType.LABORATORY) {
                    Root<DiagnosticTestLaboratory> labRoot = criteriaQuery.from(DiagnosticTestLaboratory.class);

                    // Link: DiagnosticOrderTest.testId -> DiagnosticTestLaboratory.test.id
                    filterPredicates.add(criteriaBuilder.equal(labRoot.get("test").get("id"), orderTestRoot.get("testId")));
                    filterPredicates.add(criteriaBuilder.equal(labRoot.get("category"), category));

                    criteriaQuery.distinct(true);
                } else if (orderType == TestType.RADIOLOGY) {
                    Root<DiagnosticTestRadiology> radRoot = criteriaQuery.from(DiagnosticTestRadiology.class);

                    filterPredicates.add(criteriaBuilder.equal(radRoot.get("test").get("id"), orderTestRoot.get("testId")));
                    filterPredicates.add(criteriaBuilder.equal(radRoot.get("category"), category));

                    criteriaQuery.distinct(true);
                }
            }

            return criteriaBuilder.and(filterPredicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTest> filteredPage = diagnosticOrderTestRepository.findAll(filterSpec, pageable);

        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), filteredPage
        );

        List<DiagnosticOrderTestResponseVM> responseBody = filteredPage.getContent()
                .stream()
                .map(DiagnosticOrderTestResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrderTest] FILTER - response ready. returned={} totalElements={} totalPages={}",
                filteredPage.getNumberOfElements(), filteredPage.getTotalElements(), filteredPage.getTotalPages());
        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
    }


    // -------------------------
    // ACTIONS (status changes)
    // -------------------------


    /**
     * Action endpoint: accept a test (processingStatus transition).
     * <p>
     * Uses the currently authenticated username as the accepter.
     *
     * @param orderTestId order test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/accept")
    public ResponseEntity<DiagnosticOrderTestResponseVM> accept(@Valid @PathVariable("id") Long orderTestId) {
        LOG.debug("[DiagnosticOrderTest] ACCEPT - request received. id={}", orderTestId);
        String username = currentUsername();
        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.accept(orderTestId, username);
        LOG.debug("[DiagnosticOrderTest] ACCEPT - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Action endpoint: mark a test as ready (processingStatus transition).
     *
     * @param orderTestId order test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/mark-ready")
    public ResponseEntity<DiagnosticOrderTestResponseVM> markReady(@Valid @PathVariable("id") Long orderTestId) {
        LOG.debug("[DiagnosticOrderTest] MARK_READY - request received. id={}", orderTestId);
        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.markReady(orderTestId);
        LOG.debug("[DiagnosticOrderTest] MARK_READY - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Action endpoint: review a test (optional processingStatus transition).
     *
     * @param orderTestId order test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/review")
    public ResponseEntity<DiagnosticOrderTestResponseVM> review(@Valid @PathVariable("id") Long orderTestId) {
        // Username retrieved but not currently used in service call (kept for future audit support)
        LOG.debug("[DiagnosticOrderTest] REVIEW - request received. id={}", orderTestId);
        String username = currentUsername();

        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.review(orderTestId /*, username */);
        LOG.debug("[DiagnosticOrderTest] REVIEW - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Action endpoint: approve a test result (processingStatus transition).
     *
     * @param orderTestId order test id
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResponseVM> approve(@Valid @PathVariable("id") Long orderTestId) {
        LOG.debug("[DiagnosticOrderTest] APPROVE - request received. id={}", orderTestId);
        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.approve(orderTestId);
        LOG.debug("[DiagnosticOrderTest] APPROVE - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Action endpoint: reject a test.
     * <p>
     * Uses the currently authenticated username as the rejecter.
     *
     * @param rejectRequest order  test id
     * @param orderTestId   rejection payload (reason)
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResponseVM> reject(
            @PathVariable("id") Long orderTestId,
            @Valid @RequestBody DiagnosticOrderTestRejectDTO rejectRequest
    ) {
        LOG.debug("[DiagnosticOrderTest] REJECT - request received. id={} payload={}", orderTestId, rejectRequest);
        String username = currentUsername();
        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.reject(
                orderTestId,
                username,
                rejectRequest.rejectedReason()
        );
        LOG.debug("[DiagnosticOrderTest] REJECT - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
    }

    /**
     * Action endpoint: cancel a test.
     * <p>
     * Uses the currently authenticated username as the canceller.
     *
     * @param orderTestId   order test id
     * @param cancelRequest cancellation payload (reason)
     * @return updated entity response
     */
    @PostMapping("/diagnostic-order-tests/{id}/cancel")
    public ResponseEntity<DiagnosticOrderTestResponseVM> cancel(
            @PathVariable("id") Long orderTestId,
            @Valid @RequestBody DiagnosticOrderTestCancelDTO cancelRequest
    ) {
        LOG.debug("[DiagnosticOrderTest] CANCEL - request received. id={} payload={}", orderTestId, cancelRequest);
        String username = currentUsername();
        DiagnosticOrderTest updatedTest = diagnosticOrderTestStatusService.cancel(
                orderTestId,
                username,
                cancelRequest.cancellationReason()
        );
        LOG.debug("[DiagnosticOrderTest] CANCEL - done. id={}", updatedTest.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResponseVM.ofEntity(updatedTest));
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


