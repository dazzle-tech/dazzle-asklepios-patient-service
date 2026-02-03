package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.client.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;
import com.dazzle.asklepios.domain.LabResultLog;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.repository.LabResultLogRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.NormalRangeMatcherService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
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
import java.util.Objects;
import java.util.stream.Collectors;

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

    private final DiagnosticOrderTestResultService service;
    private final DiagnosticOrderTestResultStatusService statusService;
    private final DiagnosticOrderTestResultRepository repository;
    private final SetupServiceClient setupServiceClient;
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final LabResultLogRepository logRepository;
    private final DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository;
    /**
     * Creates a new controller instance.
     *
     * @param service service for create/update/delete operations
     * @param statusService service for lifecycle transitions (review/approve/reject) and syncing parent statuses
     * @param repository repository for fetching existing results and filter queries
     */
    public DiagnosticOrderTestResultController(
            DiagnosticOrderTestResultService service,
            DiagnosticOrderTestResultStatusService statusService,
            DiagnosticOrderTestResultRepository repository, SetupServiceClient setupServiceClient, NormalRangeMatcherService normalRangeMatcherService, DiagnosticOrderRepository diagnosticOrderRepository, LabResultLogRepository logRepository, DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository
    ) {
        this.service = service;
        this.statusService = statusService;
        this.repository = repository;
        this.setupServiceClient = setupServiceClient;
        this.normalRangeMatcherService = normalRangeMatcherService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.logRepository = logRepository;

        this.diagnosticOrderTestResultTechnicianNoteRepository = diagnosticOrderTestResultTechnicianNoteRepository;
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
     * @param dto payload for creating a test result
     * @return created result mapped to response VM (HTTP 201)
     */
    @PostMapping("/diagnostic-order-tests-results")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestResultCreateDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] CREATE - request received. payload={}", dto);

        DiagnosticOrderTestResult saved = service.create(dto);

        LOG.debug("[DiagnosticOrderTestResult] CREATE - created successfully. id={} orderTestId={}",
                saved.getId(), saved.getOrderTestId());

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-tests-results/" + saved.getId()))
                .body(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    /**
     * Updates an existing {@link DiagnosticOrderTestResult}.
     *
     * <p>This endpoint updates result data fields only (value/marker/normal range, and identifiers).
     * It does not perform approve/reject transitions; those are handled by the status endpoints.</p>
     *
     * @param id result id (path variable)
     * @param dto payload for update
     * @return updated result mapped to response VM (HTTP 200)
     * @throws BadRequestAlertException if the result does not exist
     */
    @PutMapping("/diagnostic-order-tests-results/{id}")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultUpdateDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] UPDATE - request received. id={} payload={}", id, dto);

        DiagnosticOrderTestResult existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));

        // Ensure DTO id matches the path id
        DiagnosticOrderTestResultUpdateDTO fixed = new DiagnosticOrderTestResultUpdateDTO(
                id,
                dto.orderId(),
                dto.orderTestId(),
                dto.profileTestId(),
                dto.resultValueNumber(),
                dto.resultValueText(),
                dto.marker(),
                dto.normalRangeValue()
        );

        DiagnosticOrderTestResult updated = service.update(existing, fixed);

        LOG.debug("[DiagnosticOrderTestResult] UPDATE - updated successfully. id={}", updated.getId());
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(updated));
    }

    /**
     * Toggles review state for a result.
     *
     * <p>If the result has no {@code reviewDate}, it sets {@code reviewBy} and {@code reviewDate}.
     * If it is already reviewed, it clears {@code reviewBy} and {@code reviewDate}.</p>
     *
     * @param id result id
     * @return updated result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/toggle-review")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> toggleReview(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrderTestResult] TOGGLE_REVIEW - request received. id={}", id);

        String username = currentUsername();
        DiagnosticOrderTestResult saved = statusService.toggleReview(id, username);

        LOG.debug("[DiagnosticOrderTestResult] TOGGLE_REVIEW - done. id={} reviewed={}",
                saved.getId(), saved.getReviewDate() != null);

        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    /**
     * Approves a result.
     *
     * <p>Rules are enforced by {@link DiagnosticOrderTestResultStatusService#approve(Long, String,TestResultMarker,String)}.
     * On success, it also updates the parent test status and recomputes aggregated order statuses.</p>
     *
     * @param id result id
     * @return approved result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/approve")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> approve(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrderTestResult] APPROVE - request received. id={}", id);

        String username = currentUsername();
        DiagnosticOrderTestResult r = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));

        Long patientId = diagnosticOrderRepository.findById(r.getOrderId())
                .map(o -> o.getPatientId())
                .orElse(null);

        TestResultMarker viewMarker = r.getMarker();
        String viewNormalRange = r.getNormalRangeValue();
        TestResultType resultType;
        try {
            resultType = setupServiceClient.getResultTypeByProfileTestIdInternal(r.getProfileTestId());
        } catch (Exception e) {
            throw new BadRequestAlertException("setup_service_error", "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId " + r.getProfileTestId());
        }
        if (patientId != null) {
            NormalRangeMatchDTO best = normalRangeMatcherService.findBestNormalRange(r.getProfileTestId(), patientId);
            viewMarker = NormalRangeMatcherService.calculateMarker(
                    resultType,
                    r.getResultValueNumber(),
                    r.getResultValueText(),
                    best
            );

            // optional: compute a display string (you can implement it in matcher service)
            viewNormalRange = buildViewNormalRange(best);
        }
        DiagnosticOrderTestResult saved = statusService.approve(id, username,viewMarker,viewNormalRange);

        LOG.debug("[DiagnosticOrderTestResult] APPROVE - done. id={} approvedBy={}", saved.getId(), username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }

    /**
     * Rejects a result.
     *
     * <p>Rules are enforced by {@link DiagnosticOrderTestResultStatusService#reject(Long, String, String)}.
     * On success, it also updates the parent test status and recomputes aggregated order statuses.</p>
     *
     * @param id result id
     * @param dto payload containing rejected reason
     * @return rejected result mapped to response VM (HTTP 200)
     */
    @PostMapping("/diagnostic-order-tests-results/{id}/reject")
    public ResponseEntity<DiagnosticOrderTestResultResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderTestResultRejectDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResult] REJECT - request received. id={} payload={}", id, dto);

        String username = currentUsername();
        DiagnosticOrderTestResult saved = statusService.reject(id, username, dto.rejectedReason());

        LOG.debug("[DiagnosticOrderTestResult] REJECT - done. id={} rejectedBy={}", saved.getId(), username);
        return ResponseEntity.ok(DiagnosticOrderTestResultResponseVM.ofEntity(saved));
    }


    /**
     * Filters diagnostic order test results (exact matching).
     *
     * <p>Returns a paginated list of {@link DiagnosticOrderTestResult} records using optional query parameters.
     * Filters are applied with exact semantics and optional date ranges.</p>
     *
     * <p>Notes:
     * <ul>
     *   <li>{@code marker} matches exactly (if provided).</li>
     *   <li>{@code markerIn} matches any of the provided markers (ignored if {@code marker} is provided).</li>
     *   <li>{@code excludeMarker} excludes a specific marker.</li>
     *   <li>Dates are inclusive bounds.</li>
     * </ul>
     * </p>
     *
     * @param orderIds          optional diagnostic order id
     * @param orderTestId      optional diagnostic order test id
     * @param profileTestId    optional profile test id
     * @param markerIn         optional list of markers to include (IN filter).
     *                         Example (abnormal-only): UPPER_LIMIT, LOWER_LIMIT, ABNORMAL_MARKER, CRITICAL_UPPER, CRITICAL_LOWER
     *                         (i.e., all except NORMAL_MARKER and UNKNOWN).
     * @param excludeMarker    optional marker to exclude
     * @param processingStatus optional processing status (exact match)
     * @param reviewed         optional: if true returns only reviewed (reviewDate not null), if false returns only not reviewed (reviewDate null)
     * @param approvedBy       optional approvedBy (exact match)
     * @param rejectedBy       optional rejectedBy (exact match)
     * @param reviewBy         optional reviewBy (exact match)
     * @param approvedDateFrom optional lower bound (inclusive) for approvedDate
     * @param approvedDateTo   optional upper bound (inclusive) for approvedDate
     * @param rejectedDateFrom optional lower bound (inclusive) for rejectedDate
     * @param rejectedDateTo   optional upper bound (inclusive) for rejectedDate
     * @param reviewDateFrom   optional lower bound (inclusive) for reviewDate
     * @param reviewDateTo     optional upper bound (inclusive) for reviewDate
     * @param resultType       optional result type (currently used to compute marker correctly until fetched from setup-service)
     * @param pageable         pagination and sorting
     * @return list of results mapped to response VMs with pagination headers (HTTP 200)
     */
    @GetMapping("/diagnostic-order-tests-results")
    public ResponseEntity<List<DiagnosticOrderTestResultResponseVM>> filter(
            @RequestParam(name = "orderIds", required = false) List<Long> orderIds,
            @RequestParam(name = "orderTestId", required = false) Long orderTestId,
            @RequestParam(name = "profileTestId", required = false) Long profileTestId,
            @RequestParam(name = "markerIn", required = false) List<TestResultMarker> markerIn,
            @RequestParam(name = "excludeMarker", required = false) TestResultMarker excludeMarker,
            @RequestParam(name = "processingStatus", required = false) DiagnosticStatus processingStatus,
            @RequestParam(name = "reviewed", required = false) Boolean reviewed,
            @RequestParam(name = "approvedBy", required = false) String approvedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,
            @RequestParam(name = "reviewBy", required = false) String reviewBy,

            @RequestParam(name = "approvedDateFrom", required = false) Instant approvedDateFrom,
            @RequestParam(name = "approvedDateTo", required = false) Instant approvedDateTo,

            @RequestParam(name = "rejectedDateFrom", required = false) Instant rejectedDateFrom,
            @RequestParam(name = "rejectedDateTo", required = false) Instant rejectedDateTo,

            @RequestParam(name = "reviewDateFrom", required = false) Instant reviewDateFrom,
            @RequestParam(name = "reviewDateTo", required = false) Instant reviewDateTo,

            @RequestParam(name = "resultType", required = false) TestResultType resultType,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTestResult] FILTER - request received. orderIds={} orderTestId={} profileTestId={} markerIn={} excludeMarker={} processingStatus={} reviewed={} approvedBy={} rejectedBy={} reviewBy={} approvedDateFrom={} approvedDateTo={} rejectedDateFrom={} rejectedDateTo={} reviewDateFrom={} reviewDateTo={} resultType={} pageable={}",
                orderIds, orderTestId, profileTestId, markerIn, excludeMarker, processingStatus, reviewed, approvedBy, rejectedBy, reviewBy,
                approvedDateFrom, approvedDateTo, rejectedDateFrom, rejectedDateTo, reviewDateFrom, reviewDateTo, resultType, pageable);

        Specification<DiagnosticOrderTestResult> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (orderIds != null && !orderIds.isEmpty()) {
                predicates.add(root.get("orderId").in(orderIds));
            }
            if (orderTestId != null) predicates.add(cb.equal(root.get("orderTestId"), orderTestId));
            if (profileTestId != null) predicates.add(cb.equal(root.get("profileTestId"), profileTestId));

            if (markerIn != null && !markerIn.isEmpty()) {
                predicates.add(root.get("marker").in(markerIn));
            }

            if (excludeMarker != null) predicates.add(cb.notEqual(root.get("marker"), excludeMarker));
            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));

            if (approvedBy != null && !approvedBy.isBlank()) predicates.add(cb.equal(root.get("approvedBy"), approvedBy));
            if (rejectedBy != null && !rejectedBy.isBlank()) predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));
            if (reviewBy != null && !reviewBy.isBlank()) predicates.add(cb.equal(root.get("reviewBy"), reviewBy));

            if (approvedDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("approvedDate"), approvedDateFrom));
            if (approvedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("approvedDate"), approvedDateTo));

            if (rejectedDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("rejectedDate"), rejectedDateFrom));
            if (rejectedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("rejectedDate"), rejectedDateTo));

            if (reviewDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("reviewDate"), reviewDateFrom));
            if (reviewDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("reviewDate"), reviewDateTo));

            if (reviewed != null) {
                predicates.add(reviewed ? cb.isNotNull(root.get("reviewDate")) : cb.isNull(root.get("reviewDate")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTestResult> page = repository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestResultResponseVM> body = page.getContent()
                .stream()
                .map(r -> {
                    Long patientId = diagnosticOrderRepository.findById(r.getOrderId())
                            .map(o -> o.getPatientId())
                            .orElse(null);

                    TestResultMarker viewMarker = r.getMarker();
                    String viewNormalRange = r.getNormalRangeValue();
                    TestResultType resultTypes;
                    try {
                        resultTypes = setupServiceClient.getResultTypeByProfileTestIdInternal(r.getProfileTestId());
                    } catch (Exception e) {
                        throw new BadRequestAlertException(
                                "setup_service_error",
                                "diagnostic_order_tests_result",
                                "Failed to fetch result type for profileTestId " + r.getProfileTestId()
                        );
                    }

                    if (patientId != null) {
                        NormalRangeMatchDTO best = normalRangeMatcherService.findBestNormalRange(r.getProfileTestId(), patientId);
                        viewMarker = NormalRangeMatcherService.calculateMarker(
                                resultTypes,
                                r.getResultValueNumber(),
                                r.getResultValueText(),
                                best
                        );
                        viewNormalRange = buildViewNormalRange(best);
                    }

                    return DiagnosticOrderTestResultResponseVM.ofEntityWithViewAndNote(
                            r, viewMarker, viewNormalRange,
                            diagnosticOrderTestResultTechnicianNoteRepository.existsByResultId(r.getId())
                    );
                })
                .toList();

        LOG.debug("[DiagnosticOrderTestResult] FILTER - response ready. returned={} totalElements={} totalPages={}",
                body.size(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // helper in controller (or move to service)
    private String buildViewNormalRange(NormalRangeMatchDTO best) {
        if (best == null) return null;

        if (best.resultText() != null && !best.resultText().isBlank()) return best.resultText();
        if (best.resultLov() != null && !best.resultLov().isBlank()) return best.resultLov();

        Double from = best.rangeFrom();
        Double to = best.rangeTo();
        if (from != null && to != null) return from + " - " + to;
        if (from != null) return ">= " + from;
        if (to != null) return "<= " + to;

        return null;
    }
    @GetMapping("/lab-result-logs/by-result/{resultId}")
    public ResponseEntity<List<LabResultLogResponseVM>> getByResultId(@PathVariable Long resultId) {
        LOG.debug("[LabResultLog] GET_BY_RESULT_ID - request received. resultId={}", resultId);

        List<LabResultLog> logs = logRepository.findAllByResultIdOrderByResultDateDesc(resultId);

        List<LabResultLogResponseVM> body = logs.stream()
                .map(LabResultLogResponseVM::ofEntity)
                .toList();

        LOG.debug("[LabResultLog] GET_BY_RESULT_ID - response ready. resultId={} returned={}", resultId, body.size());
        return ResponseEntity.ok(body);
    }
    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids")
    public ResponseEntity<List<Long>> findFilledProfileTestIds(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> ids = orderTestIds.stream()
                .filter(Objects::nonNull)
                .flatMap(orderTestId -> repository
                        .findDistinctProfileTestIdsByOrderTestId(orderTestId)
                        .stream()
                )
                .distinct()
                .toList();

        return ResponseEntity.ok(ids);
    }

    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids/by-order-test")
    public ResponseEntity<Map<Long, List<Long>>> findFilledProfileTestIdsByOrderTest(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }

        Map<Long, List<Long>> map = orderTestIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> repository.findDistinctProfileTestIdsByOrderTestId(id)
                ));

        return ResponseEntity.ok(map);
    }

}
