package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultRejectDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.RejectResultDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.persistence.criteria.Predicate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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


    // =========================================================
    // FILTER
    // =========================================================

    @GetMapping("/diagnostic-order-tests-results")
    public ResponseEntity<List<DiagnosticOrderTestResultResponseVM>> filter(

            @RequestParam(name = "orderIdIn", required = false)
            List<Long> orderIdInFilter,

            @RequestParam(name = "orderTestId", required = false)
            Long orderTestIdFilter,

            @RequestParam(name = "profileTestId", required = false)
            Long profileTestIdFilter,

            @RequestParam(name = "marker", required = false)
            TestResultMarker markerFilter,

            @RequestParam(name = "excludeMarker", required = false)
            TestResultMarker excludeMarkerFilter,

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

        Specification<DiagnosticOrderTestResult> resultSpecification = (testResultRoot, criteriaQuery, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            // =============================
            // BASIC FILTERS
            // =============================
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

            // =============================
            // MARKER FILTERS
            // =============================

            if (markerFilter != null) {
                predicates.add(criteriaBuilder.equal(testResultRoot.get("marker"), markerFilter));
            }

            if (excludeMarkerFilter != null) {
                predicates.add(criteriaBuilder.notEqual(testResultRoot.get("marker"), excludeMarkerFilter));
            }

            // =============================
            // USER FILTERS
            // =============================

            if (approvedByFilter != null) {
                predicates.add(criteriaBuilder.equal(testResultRoot.get("approvedBy"), approvedByFilter));
            }

            if (rejectedByFilter != null) {
                predicates.add(criteriaBuilder.equal(testResultRoot.get("rejectedBy"), rejectedByFilter));
            }

            if (reviewByFilter != null) {
                predicates.add(criteriaBuilder.equal(testResultRoot.get("reviewBy"), reviewByFilter));
            }

            // =============================
            // DATE FILTERS
            // =============================

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
                if (reviewed) {
                    predicates.add(criteriaBuilder.isNotNull(testResultRoot.get("reviewDate")));
                } else {
                    predicates.add(criteriaBuilder.isNull(testResultRoot.get("reviewDate")));
                }
            }


            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };


        Page<DiagnosticOrderTestResultResponseVM> page =
                service.resultFilter(resultSpecification, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    // =========================================================
    // INTERNAL ENDPOINTS
    // =========================================================

    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids")
    public ResponseEntity<List<Long>> findFilledProfileTestIds(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        return ResponseEntity.ok(
                service.findFilledProfileTestIds(orderTestIds)
        );
    }

    @GetMapping("/diagnostic-order-tests-results/internal/filled-profile-test-ids/by-order-test")
    public ResponseEntity<Map<Long, List<Long>>> findFilledProfileTestIdsByOrderTest(
            @RequestParam(name = "orderTestIds") List<Long> orderTestIds
    ) {
        return ResponseEntity.ok(
                service.findFilledProfileTestIdsByOrderTest(orderTestIds)
        );
    }
}
