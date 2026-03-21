package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.LabResultLog;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.BulkIdsDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.BulkRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.RejectResultDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
import com.dazzle.asklepios.web.rest.vm.laboratory.LabResultLogResponseVM;
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
import java.util.Map;

/**
 * REST controller for managing {@link DiagnosticOrderTestResult}.
 *
 * <p>Exposes endpoints to:
 * <ul>
 *   <li>Create and update test results</li>
 *   <li>Change lifecycle state (review / approve / reject)</li>
 *   <li>Filter/search results using query parameters with pagination</li>
 *   <li>Internal helper endpoints for filled profile test ids</li>
 * </ul>
 * </p>
 *
 * <p>Notes:
 * <ul>
 *   <li>Approve uses business logic inside {@link DiagnosticOrderTestResultService} to compute view marker/normal range.</li>
 *   <li>Reject delegates to {@link DiagnosticOrderTestResultStatusService} to enforce lifecycle transitions.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestResultController {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultController.class);

    private final DiagnosticOrderTestResultService service;
    private final DiagnosticOrderTestResultStatusService statusService;

    public DiagnosticOrderTestResultController(
            DiagnosticOrderTestResultService service,
            DiagnosticOrderTestResultStatusService statusService
    ) {
        this.service = service;
        this.statusService = statusService;
    }

    /**
     * Resolves the current authenticated username.
     *
     * @return current user login/username
     * @throws BadRequestAlertException if no authenticated user is available
     */
    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests_result",
                        "No authenticated user"
                ));
    }

    // =========================================================
    // CREATE
    // =========================================================

    /**
     * Creates a new {@link DiagnosticOrderTestResult}.
     *
     * <p>Creates a result record with {@link DiagnosticStatus#RESULT_READY} and triggers recomputation
     * of parent test status based on results.</p>
     *
     * @param dto create payload
     * @return created result mapped to response VM (HTTP 201)
     */
    @PostMapping("/diagnostic-order-tests-results")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestResultCreateDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] CREATE - payload={}", dto);

        DiagnosticOrderTestResult saved = service.create(dto);

        return ResponseEntity
                .created(URI.create(
                        "/api/patient/diagnostic-order-tests-results/" + saved.getId()
                ))
                .body(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    // =========================================================
    // UPDATE
    // =========================================================

    /**
     * Updates an existing {@link DiagnosticOrderTestResult}.
     *
     * <p>This endpoint updates only result fields (value/marker/normalRange and identifiers).
     * Lifecycle transitions (approve/reject/review) are handled by dedicated endpoints.</p>
     *
     * @param id  result id (path variable)
     * @param dto update payload
     * @return updated result mapped to response VM (HTTP 200)
     */
    @PutMapping("/diagnostic-order-tests-results/{id}")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultUpdateDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] UPDATE - id={} payload={}", id, dto);

        DiagnosticOrderTestResult updated =
                service.updateWithValidation(id, dto);

        return ResponseEntity.ok(
                DiagnosticOrderTestResultResponseVM.ofEntity(updated)
        );
    }

    // =========================================================
    // TOGGLE REVIEW
    // =========================================================

    /**
     * Toggles review state for a result.
     *
     * <p>If the result is not reviewed, sets reviewBy/reviewDate.
     * If it is already reviewed, clears reviewBy/reviewDate.</p>
     *
     * @param id result id
     * @return updated result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/toggle-review")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> toggleReview(
            @PathVariable Long id
    ) {
        String username = currentUsername();

        DiagnosticOrderTestResult updated =
                statusService.toggleReview(id, username);

        return ResponseEntity.ok(
                DiagnosticOrderTestResultResponseVM.ofEntity(updated)
        );
    }

    // =========================================================
    // APPROVE (BUSINESS LOGIC INSIDE SERVICE)
    // =========================================================

    /**
     * Approves a result.
     *
     * <p>Approval business logic (computing view marker and normal range based on patient and profile test)
     * is implemented inside {@link DiagnosticOrderTestResultService#approveResult(Long, String)}.</p>
     *
     * @param id result id
     * @return approved result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> approve(
            @PathVariable Long id
    ) {
        String username = currentUsername();

        DiagnosticOrderTestResult saved =
                service.approveResult(id, username);

        return ResponseEntity.ok(
                DiagnosticOrderTestResultResponseVM.ofEntity(saved)
        );
    }

    // =========================================================
    // REJECT
    // =========================================================

    /**
     * Rejects a result.
     *
     * <p>Delegates lifecycle validation and audit field updates to
     * {@link DiagnosticOrderTestResultStatusService}.</p>
     *
     * @param id  result id
     * @param dto payload containing rejected reason
     * @return rejected result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultRejectDTO dto
    ) {
        String username = currentUsername();

        DiagnosticOrderTestResult saved = statusService.reject(
                new RejectResultDTO(
                        id,
                        username,
                        dto.rejectedReason()
                )
        );

        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    /**
     * Filters diagnostic order test results using optional query parameters with pagination.
     *
     * <p>Supported filters:
     * <ul>
     *   <li>orderIdIn: matches results whose orderTestId belongs to a DiagnosticOrderTest under the given order ids</li>
     *   <li>orderTestId / profileTestId / processingStatus</li>
     *   <li>marker / excludeMarker</li>
     *   <li>approvedBy / rejectedBy / reviewBy</li>
     *   <li>date ranges for approved/rejected/review dates</li>
     *   <li>reviewed: if true returns only reviewed (reviewDate not null), if false returns only not reviewed</li>
     * </ul>
     * </p>
     *
     * @param orderIdInFilter        order ids to filter by (via subquery on {@link DiagnosticOrderTest})
     * @param orderTestIdFilter      exact order test id filter
     * @param profileTestIdFilter    exact profile test id filter
     * @param markerFilter           exact marker filter
     * @param excludeMarkerFilter    marker exclusion filter
     * @param processingStatusFilter processing status filter
     * @param approvedByFilter       approved by filter
     * @param rejectedByFilter       rejected by filter
     * @param reviewByFilter         review by filter
     * @param approvedDateFromFilter approved date lower bound (inclusive)
     * @param approvedDateToFilter   approved date upper bound (inclusive)
     * @param rejectedDateFromFilter rejected date lower bound (inclusive)
     * @param rejectedDateToFilter   rejected date upper bound (inclusive)
     * @param reviewDateFromFilter   review date lower bound (inclusive)
     * @param reviewDateToFilter     review date upper bound (inclusive)
     * @param reviewed               reviewed flag filter
     * @param pageable               pagination information
     * @return page content mapped to response VMs with pagination headers (HTTP 200)
     */
    @GetMapping("/diagnostic-order-tests-results")
    public ResponseEntity<List<DiagnosticOrderTestResultResponseVM>> filter(
            @RequestParam(name = "orderIdIn", required = false)
            List<Long> orderIdInFilter,

            @RequestParam(name = "orderTestId", required = false)
            Long orderTestIdFilter,

            @RequestParam(name = "profileTestId", required = false)
            Long profileTestIdFilter,

            @RequestParam(name = "markerIn", required = false)
            List<TestResultMarker> markerInFilter,

            @RequestParam(name = "excludeMarkerIn", required = false)
            List<TestResultMarker> excludeMarkerInFilter,


            @RequestParam(name = "processingStatus", required = false)
            DiagnosticStatus processingStatusFilter,

            @RequestParam(name = "approvedBy", required = false)
            String approvedByFilter,

            @RequestParam(name = "rejectedBy", required = false)
            String rejectedByFilter,

            @RequestParam(name = "reviewBy", required = false)
            String reviewByFilter,

            @RequestParam(name = "approvedDateFrom", required = false)
            Instant approvedDateFromFilter,

            @RequestParam(name = "approvedDateTo", required = false)
            Instant approvedDateToFilter,

            @RequestParam(name = "rejectedDateFrom", required = false)
            Instant rejectedDateFromFilter,

            @RequestParam(name = "rejectedDateTo", required = false)
            Instant rejectedDateToFilter,

            @RequestParam(name = "reviewDateFrom", required = false)
            Instant reviewDateFromFilter,

            @RequestParam(name = "reviewDateTo", required = false)
            Instant reviewDateToFilter,

            @RequestParam(name = "reviewed", required = false)
            Boolean reviewed,

            @ParameterObject Pageable pageable
    ) {
        Specification<DiagnosticOrderTestResult> resultSpecification =
                (testResultRoot, criteriaQuery, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (orderIdInFilter != null && !orderIdInFilter.isEmpty()) {
                        var subQuery = criteriaQuery.subquery(Long.class);
                        var testRoot = subQuery.from(DiagnosticOrderTest.class);

                        subQuery.select(testRoot.get("id"))
                                .where(testRoot.get("orderId").in(orderIdInFilter));

                        predicates.add(testResultRoot.get("orderTestId").in(subQuery));
                    }

                    if (orderTestIdFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("orderTestId"), orderTestIdFilter));
                    }

                    if (profileTestIdFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("profileTestId"), profileTestIdFilter));
                    }

                    if (processingStatusFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("processingStatus"), processingStatusFilter));
                    }

                    // marker IN
                    if (markerInFilter != null && !markerInFilter.isEmpty()) {
                        predicates.add(
                                testResultRoot.get("marker").in(markerInFilter)
                        );
                    }

// marker NOT IN
                    if (excludeMarkerInFilter != null && !excludeMarkerInFilter.isEmpty()) {
                        predicates.add(
                                criteriaBuilder.not(
                                        testResultRoot.get("marker").in(excludeMarkerInFilter)
                                )
                        );
                    }


                    if (approvedByFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("approvedBy"), approvedByFilter));
                    }

                    if (rejectedByFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("rejectedBy"), rejectedByFilter));
                    }

                    if (reviewByFilter != null) {
                        predicates.add(criteriaBuilder.equal(testResultRoot.get("reviewBy"), reviewByFilter));
                    }

                    if (approvedDateFromFilter != null) {
                        predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("approvedDate"),
                                approvedDateFromFilter
                        ));
                    }

                    if (approvedDateToFilter != null) {
                        predicates.add(criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("approvedDate"),
                                approvedDateToFilter
                        ));
                    }

                    if (rejectedDateFromFilter != null) {
                        predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("rejectedDate"),
                                rejectedDateFromFilter
                        ));
                    }

                    if (rejectedDateToFilter != null) {
                        predicates.add(criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("rejectedDate"),
                                rejectedDateToFilter
                        ));
                    }

                    if (reviewDateFromFilter != null) {
                        predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("reviewDate"),
                                reviewDateFromFilter
                        ));
                    }

                    if (reviewDateToFilter != null) {
                        predicates.add(criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("reviewDate"),
                                reviewDateToFilter
                        ));
                    }

                    if (reviewed != null) {
                        predicates.add(reviewed
                                ? criteriaBuilder.isNotNull(testResultRoot.get("reviewDate"))
                                : criteriaBuilder.isNull(testResultRoot.get("reviewDate")));
                    }

                    return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
                };

        Page<DiagnosticOrderTestResultResponseVM> page =
                service.resultFilter(resultSpecification, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    // =========================================================
    // INTERNAL ENDPOINTS
    // =========================================================

    /**
     * Returns profile test ids that have results for the given order test ids.
     *
     * @param orderTestIds order test ids
     * @return distinct filled profile test ids
     */
    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids")
    public ResponseEntity<List<Long>> findFilledProfileTestIds(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        return ResponseEntity.ok(
                service.findFilledProfileTestIds(orderTestIds)
        );
    }

    /**
     * Returns a map of orderTestId -&gt; list of distinct profile test ids that have results.
     *
     * @param orderTestIds order test ids
     * @return map keyed by order test id, values are filled profile test ids
     */
    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids/by-order-test")
    public ResponseEntity<Map<Long, List<Long>>> findFilledProfileTestIdsByOrderTest(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        return ResponseEntity.ok(
                service.findFilledProfileTestIdsByOrderTest(orderTestIds)
        );
    }

    @GetMapping("/lab-result-logs/by-result/{resultId}")
    public ResponseEntity<List<LabResultLogResponseVM>> getByResultId(@PathVariable Long resultId) {
        LOG.debug("[LabResultLog] GET_BY_RESULT_ID - request received. resultId={}", resultId);

        List<LabResultLog> labResultLogs = service.findLabResultLogsByResultId(resultId);

        List<LabResultLogResponseVM> body = labResultLogs.stream()
                .map(LabResultLogResponseVM::ofEntity)
                .toList();

        LOG.debug("[LabResultLog] GET_BY_RESULT_ID - response ready. resultId={} returned={}", resultId, body.size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/diagnostic-order-tests-results/bulk-approve")
    public ResponseEntity<Void> bulkApprove(@Valid @RequestBody BulkIdsDTO dto) {
        LOG.debug("REST bulk-approve DiagnosticOrderTestResult count={} ids={}",
                dto.ids().size(), dto.ids());
        service.bulkApproveResults(dto.ids(), currentUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/diagnostic-order-tests-results/bulk-reject")
    public ResponseEntity<Void> bulkReject(@Valid @RequestBody BulkRejectDTO dto) {
        LOG.debug("REST bulk-reject DiagnosticOrderTestResult count={} ids={} reason={}",
                dto.ids().size(), dto.ids(), dto.rejectedReason());
        statusService.bulkReject(dto.ids(), currentUsername(), dto.rejectedReason());
        return ResponseEntity.ok().build();
    }
}
