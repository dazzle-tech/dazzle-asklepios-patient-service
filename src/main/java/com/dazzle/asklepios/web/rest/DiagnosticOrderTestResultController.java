package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.NormalRangeMatcherService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
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
 * REST controller for managing {@link DiagnosticOrderTestResult} resources.
 *
 * <p>This controller exposes endpoints to:</p>
 * <ul>
 *   <li>Create and update test results</li>
 *   <li>Change result lifecycle states (review / approve / reject)</li>
 *   <li>Filter/search results using exact-match query parameters with pagination</li>
 * </ul>
 *
 * <p>Status transitions (approve/reject/review) are delegated to
 * {@link DiagnosticOrderTestResultStatusService} to enforce rules and keep parent test/order in sync.</p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestResultController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestResultController.class);

    private final DiagnosticOrderTestResultService diagnosticOrderTestResultService;
    private final DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService;
    private final SetupServiceClient setupServiceClient;
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderService diagnosticOrderService;

    /**
     * Creates a new controller instance.
     *
     * @param diagnosticOrderTestResultService       service for create/update/filter operations
     * @param diagnosticOrderTestResultStatusService service for lifecycle transitions (review/approve/reject)
     */
    public DiagnosticOrderTestResultController(
            DiagnosticOrderTestResultService diagnosticOrderTestResultService,
            DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService, SetupServiceClient setupServiceClient, NormalRangeMatcherService normalRangeMatcherService, DiagnosticOrderService diagnosticOrderService
    ) {
        this.diagnosticOrderTestResultService = diagnosticOrderTestResultService;
        this.diagnosticOrderTestResultStatusService = diagnosticOrderTestResultStatusService;
        this.setupServiceClient = setupServiceClient;
        this.normalRangeMatcherService = normalRangeMatcherService;
        this.diagnosticOrderService = diagnosticOrderService;
    }

    /**
     * Returns the current authenticated username.
     *
     * @return current user login
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

    /**
     * Creates a new {@link DiagnosticOrderTestResult}.
     *
     * <p>Business rules are enforced by {@link DiagnosticOrderTestResultService#create}:
     * it sets the result processing status to {@link DiagnosticStatus#RESULT_READY} and triggers
     * recomputation of the parent test processing status from all its results.</p>
     *
     * @param requestDto payload for creating a test result
     * @return created result mapped to response VM (HTTP 201)
     */
    @PostMapping("/diagnostic-order-tests-results")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestResultCreateDTO requestDto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] CREATE - request received. payload={}", requestDto);

        DiagnosticOrderTestResult createdResult = diagnosticOrderTestResultService.create(requestDto);

        LOG.debug("[DiagnosticOrderTestResult] CREATE - created successfully. id={} orderTestId={}",
                createdResult.getId(), createdResult.getOrderTestId());

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests-results/" + createdResult.getId()))
                .body(DiagnosticOrderTestResultResponseVM.ofEntity(createdResult));
    }

    /**
     * Updates an existing {@link DiagnosticOrderTestResult}.
     *
     * <p>This endpoint updates result data fields only (value/marker/normal range, and identifiers).
     * It does not perform approve/reject transitions; those are handled by the status endpoints.</p>
     *
     * @param resultId   result id (path variable)
     * @param requestDto payload for update
     * @return updated result mapped to response VM (HTTP 200)
     * @throws BadRequestAlertException if the result does not exist
     */
    @PutMapping("/diagnostic-order-tests-results/{id}")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> update(
            @PathVariable("id") Long resultId,
            @Valid @RequestBody DiagnosticOrderTestResultUpdateDTO requestDto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] UPDATE - request received. id={} payload={}", resultId, requestDto);

        DiagnosticOrderTestResult updatedResult = diagnosticOrderTestResultService.updateById(resultId, requestDto);

        LOG.debug("[DiagnosticOrderTestResult] UPDATE - updated successfully. id={}", updatedResult.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(updatedResult));
    }

    /**
     * Toggles review state for a result.
     *
     * <p>If the result has no {@code reviewDate}, it sets {@code reviewBy} and {@code reviewDate}.
     * If it is already reviewed, it clears {@code reviewBy} and {@code reviewDate}.</p>
     *
     * @param resultId result id
     * @return updated result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/toggle-review")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> toggleReview(@PathVariable("id") Long resultId) {
        LOG.debug("[DiagnosticOrderTestResult] TOGGLE_REVIEW - request received. id={}", resultId);

        String username = currentUsername();
        DiagnosticOrderTestResult updatedResult = diagnosticOrderTestResultStatusService.toggleReview(resultId, username);

        LOG.debug("[DiagnosticOrderTestResult] TOGGLE_REVIEW - done. id={} reviewed={}",
                updatedResult.getId(), updatedResult.getReviewDate() != null);

        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(updatedResult));
    }

    /**
     * Approves a result.
     *
     * <p>Rules are enforced by {@link DiagnosticOrderTestResultStatusService#approve(Long, String, TestResultMarker, String)}.
     * On success, it also updates the parent test status and recomputes aggregated order statuses.</p>
     *
     * @param resultId result id
     * @return approved result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> approve(@PathVariable("id") Long resultId) {
        LOG.debug("[DiagnosticOrderTestResult] APPROVE - request received. id={}", resultId);

        String username = currentUsername();
        DiagnosticOrderTestResult approvedResult =
                diagnosticOrderTestResultService.approveWithComputedMarker(resultId, username);

        LOG.debug("[DiagnosticOrderTestResult] APPROVE - done. id={} approvedBy={}", approvedResult.getId(), username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(approvedResult));
    }

    /**
     * Rejects a result.
     *
     * <p>Rules are enforced by {@link DiagnosticOrderTestResultStatusService#reject(Long, String, String)}.
     * On success, it also updates the parent test status and recomputes aggregated order statuses.</p>
     *
     * @param resultId      result id
     * @param rejectRequest payload containing rejected reason
     * @return rejected result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> reject(
            @PathVariable("id") Long resultId,
            @Valid @RequestBody DiagnosticOrderTestResultRejectDTO rejectRequest
    ) {
        LOG.debug("[DiagnosticOrderTestResult] REJECT - request received. id={} payload={}", resultId, rejectRequest);

        String username = currentUsername();
        DiagnosticOrderTestResult rejectedResult = diagnosticOrderTestResultStatusService.reject(
                resultId,
                username,
                rejectRequest.rejectedReason()
        );

        LOG.debug("[DiagnosticOrderTestResult] REJECT - done. id={} rejectedBy={}", rejectedResult.getId(), username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(rejectedResult));
    }


    /**
     * Filters diagnostic order test results (exact matching).
     *
     * <p>Returns a paginated list of {@link DiagnosticOrderTestResult} records using optional query parameters.
     * Filters are applied with exact semantics and optional date ranges.</p>
     *
     * <p><b>Important:</b> {@code orderId} is filtered indirectly through {@code orderTestId} because
     * {@link DiagnosticOrderTestResult} no longer contains {@code orderId} directly.</p>
     *
     * <p>Notes:
     * <ul>
     *   <li>{@code marker} matches exactly.</li>
     *   <li>{@code excludeMarker} excludes a specific marker.</li>
     *   <li>Dates are inclusive bounds.</li>
     * </ul>
     * </p>
     *
     * @param orderIdFilter          optional diagnostic order id (applied via orderTest subquery)
     * @param orderTestIdFilter      optional diagnostic order test id
     * @param profileTestIdFilter    optional profile test id
     * @param markerFilter           optional result marker (exact match)
     * @param excludeMarkerFilter    optional marker to exclude
     * @param processingStatusFilter optional processing status (exact match)
     * @param approvedByFilter       optional approvedBy (exact match)
     * @param rejectedByFilter       optional rejectedBy (exact match)
     * @param reviewByFilter         optional reviewBy (exact match)
     * @param approvedDateFromFilter optional lower bound (inclusive) for approvedDate
     * @param approvedDateToFilter   optional upper bound (inclusive) for approvedDate
     * @param rejectedDateFromFilter optional lower bound (inclusive) for rejectedDate
     * @param rejectedDateToFilter   optional upper bound (inclusive) for rejectedDate
     * @param reviewDateFromFilter   optional lower bound (inclusive) for reviewDate
     * @param reviewDateToFilter     optional upper bound (inclusive) for reviewDate
     * @param pageable               pagination and sorting
     * @return list of results mapped to response VMs with pagination headers (HTTP 200)
     */

    @GetMapping("/diagnostic-order-tests-results")
    public ResponseEntity<List<DiagnosticOrderTestResultResponseVM>> filter(
            @RequestParam(name = "orderIdIn", required = false) List<Long> orderIdInFilter,
            @RequestParam(name = "orderTestId", required = false) Long orderTestIdFilter,
            @RequestParam(name = "profileTestId", required = false) Long profileTestIdFilter,

            @RequestParam(name = "marker", required = false) TestResultMarker markerFilter,
            @RequestParam(name = "excludeMarker", required = false) TestResultMarker excludeMarkerFilter,
            @RequestParam(name = "processingStatus", required = false) DiagnosticStatus processingStatusFilter,

            @RequestParam(name = "approvedBy", required = false) String approvedByFilter,
            @RequestParam(name = "rejectedBy", required = false) String rejectedByFilter,
            @RequestParam(name = "reviewBy", required = false) String reviewByFilter,

            @RequestParam(name = "approvedDateFrom", required = false) Instant approvedDateFromFilter,
            @RequestParam(name = "approvedDateTo", required = false) Instant approvedDateToFilter,

            @RequestParam(name = "rejectedDateFrom", required = false) Instant rejectedDateFromFilter,
            @RequestParam(name = "rejectedDateTo", required = false) Instant rejectedDateToFilter,

            @RequestParam(name = "reviewDateFrom", required = false) Instant reviewDateFromFilter,
            @RequestParam(name = "reviewDateTo", required = false) Instant reviewDateToFilter,

            @ParameterObject Pageable pageable
    ) {
        Specification<DiagnosticOrderTestResult> spec = (orderTestRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();


            if (orderIdInFilter != null && !orderIdInFilter.isEmpty()) {
                var subQuery = criteriaQuery.subquery(Long.class);
                var testRoot = subQuery.from(DiagnosticOrderTest.class);

                subQuery.select(testRoot.get("id"))
                        .where(testRoot.get("orderId").in(orderIdInFilter));

                predicates.add(orderTestRoot.get("orderTestId").in(subQuery));
            }


            if (orderTestIdFilter != null)
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("orderTestId"), orderTestIdFilter));
            if (profileTestIdFilter != null)
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("profileTestId"), profileTestIdFilter));

            if (markerFilter != null) predicates.add(criteriaBuilder.equal(orderTestRoot.get("marker"), markerFilter));
            if (excludeMarkerFilter != null)
                predicates.add(criteriaBuilder.notEqual(orderTestRoot.get("marker"), excludeMarkerFilter));
            if (processingStatusFilter != null)
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("processingStatus"), processingStatusFilter));

            if (approvedByFilter != null && !approvedByFilter.isBlank())
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("approvedBy"), approvedByFilter));
            if (rejectedByFilter != null && !rejectedByFilter.isBlank())
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("rejectedBy"), rejectedByFilter));
            if (reviewByFilter != null && !reviewByFilter.isBlank())
                predicates.add(criteriaBuilder.equal(orderTestRoot.get("reviewBy"), reviewByFilter));

            if (approvedDateFromFilter != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(orderTestRoot.get("approvedDate"), approvedDateFromFilter));
            if (approvedDateToFilter != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(orderTestRoot.get("approvedDate"), approvedDateToFilter));

            if (rejectedDateFromFilter != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(orderTestRoot.get("rejectedDate"), rejectedDateFromFilter));
            if (rejectedDateToFilter != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(orderTestRoot.get("rejectedDate"), rejectedDateToFilter));

            if (reviewDateFromFilter != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(orderTestRoot.get("reviewDate"), reviewDateFromFilter));
            if (reviewDateToFilter != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(orderTestRoot.get("reviewDate"), reviewDateToFilter));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTestResult> resultsPage = diagnosticOrderTestResultService.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), resultsPage
        );

        List<DiagnosticOrderTestResultResponseVM> body =
                diagnosticOrderTestResultService.buildViewResponses(resultsPage.getContent());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids")
    public ResponseEntity<List<Long>> findFilledProfileTestIds(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        LOG.debug("[DiagnosticOrderTestResult] FILLED_PROFILE_IDS - request received. orderTestIdsCount={}",
                orderTestIds == null ? 0 : orderTestIds.size());
        List<Long> filledProfileTestIds = diagnosticOrderTestResultService.findFilledProfileTestIds(orderTestIds);
        return ResponseEntity.ok(filledProfileTestIds);
    }

    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids/by-order-test")
    public ResponseEntity<Map<Long, List<Long>>> findFilledProfileTestIdsByOrderTest(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        LOG.debug("[DiagnosticOrderTestResult] FILLED_PROFILE_IDS_BY_ORDER_TEST - request received. orderTestIdsCount={}",
                orderTestIds == null ? 0 : orderTestIds.size());
        Map<Long, List<Long>> filledProfileIdsMap =
                diagnosticOrderTestResultService.findFilledProfileTestIdsByOrderTest(orderTestIds);
        return ResponseEntity.ok(filledProfileIdsMap);
    }

}
