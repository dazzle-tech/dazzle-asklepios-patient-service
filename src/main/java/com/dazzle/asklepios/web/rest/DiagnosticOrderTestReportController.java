package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestReportService;
import com.dazzle.asklepios.service.DiagnosticOrderTestStatusService;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportResponseVM;
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for managing radiology reports (DiagnosticOrderTestReport).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Validate test constraints (existence/type/order match/prerequisites).</li>
 *   <li>Expose CRUD endpoints for reports.</li>
 *   <li>Expose controlled endpoints for review/reject and image workflow.</li>
 *   <li>Expose filtering endpoint for reports with many optional query params.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestReportController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportController.class);

    private final DiagnosticOrderTestReportService reportService;
    private final DiagnosticOrderTestRepository testRepository;
    private final DiagnosticOrderTestReportRepository reportRepository;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    public DiagnosticOrderTestReportController(
            DiagnosticOrderTestReportService reportService,
            DiagnosticOrderTestRepository testRepository,
            DiagnosticOrderTestReportRepository reportRepository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService
    ) {
        this.reportService = reportService;
        this.testRepository = testRepository;
        this.reportRepository = reportRepository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
    }

    // داخل DiagnosticOrderTestReportController

    /**
     * Filters radiology reports using only fields from diagnostic_order_tests_report.
     * <p>
     * Rules:
     * <ul>
     *   <li>Use either processingStatus or processingStatusIn, not both.</li>
     *   <li>Use either imageStatus or imageStatusIn, not both.</li>
     * </ul>
     */
    @GetMapping("/radiology/reports")
    public ResponseEntity<List<DiagnosticOrderTestReportResponseVM>> filterReports(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "orderTestId", required = false) Long orderTestId,

            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "hasReport", required = false) Boolean hasReport,

            @RequestParam(name = "approvedBy", required = false) String approvedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,
            @RequestParam(name = "reviewBy", required = false) String reviewBy,

            @RequestParam(name = "approvedDateFrom", required = false) Instant approvedDateFrom,
            @RequestParam(name = "approvedDateTo", required = false) Instant approvedDateTo,
            @RequestParam(name = "rejectedDateFrom", required = false) Instant rejectedDateFrom,
            @RequestParam(name = "rejectedDateTo", required = false) Instant rejectedDateTo,
            @RequestParam(name = "reviewDateFrom", required = false) Instant reviewDateFrom,
            @RequestParam(name = "reviewDateTo", required = false) Instant reviewDateTo,

            @RequestParam(name = "processingStatus", required = false) DiagnosticStatus processingStatus,
            @RequestParam(name = "processingStatusIn", required = false) List<DiagnosticStatus> processingStatusIn,
            @RequestParam(name = "processingStatusNotIn", required = false) List<DiagnosticStatus> processingStatusNotIn,

            @RequestParam(name = "imageStatus", required = false) RadiologyImageStatus imageStatus,
            @RequestParam(name = "imageStatusIn", required = false) List<RadiologyImageStatus> imageStatusIn,
            @RequestParam(name = "imageStatusNotIn", required = false) List<RadiologyImageStatus> imageStatusNotIn,

            @RequestParam(name = "createdDateFrom", required = false) Instant createdDateFrom,
            @RequestParam(name = "createdDateTo", required = false) Instant createdDateTo,
            @RequestParam(name = "lastModifiedDateFrom", required = false) Instant lastModifiedDateFrom,
            @RequestParam(name = "lastModifiedDateTo", required = false) Instant lastModifiedDateTo,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST filter DiagnosticOrderTestReport orderId={} orderTestId={}", orderId, orderTestId);

        if (processingStatus != null && processingStatusIn != null && !processingStatusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either processingStatus or processingStatusIn, not both");
        }
        if (imageStatus != null && imageStatusIn != null && !imageStatusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either imageStatus or imageStatusIn, not both");
        }

        Specification<DiagnosticOrderTestReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (id != null) predicates.add(cb.equal(root.get("id"), id));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (orderTestId != null) predicates.add(cb.equal(root.get("orderTestId"), orderTestId));

            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }

            if (hasReport != null) {
                if (hasReport) {
                    predicates.add(cb.isNotNull(root.get("report")));
                } else {
                    predicates.add(cb.isNull(root.get("report")));
                }
            }

            if (approvedBy != null && !approvedBy.isBlank()) predicates.add(cb.equal(root.get("approvedBy"), approvedBy));
            if (rejectedBy != null && !rejectedBy.isBlank()) predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));
            if (reviewBy != null && !reviewBy.isBlank()) predicates.add(cb.equal(root.get("reviewBy"), reviewBy));

            if (approvedDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("approvedDate"), approvedDateFrom));
            if (approvedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("approvedDate"), approvedDateTo));

            if (rejectedDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("rejectedDate"), rejectedDateFrom));
            if (rejectedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("rejectedDate"), rejectedDateTo));

            if (reviewDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("reviewDate"), reviewDateFrom));
            if (reviewDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("reviewDate"), reviewDateTo));

            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (processingStatusIn != null && !processingStatusIn.isEmpty()) predicates.add(root.get("processingStatus").in(processingStatusIn));
            if (processingStatusNotIn != null && !processingStatusNotIn.isEmpty())
                predicates.add(cb.not(root.get("processingStatus").in(processingStatusNotIn)));

            if (imageStatus != null) predicates.add(cb.equal(root.get("imageStatus"), imageStatus));
            if (imageStatusIn != null && !imageStatusIn.isEmpty()) predicates.add(root.get("imageStatus").in(imageStatusIn));
            if (imageStatusNotIn != null && !imageStatusNotIn.isEmpty())
                predicates.add(cb.not(root.get("imageStatus").in(imageStatusNotIn)));

            if (createdDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            if (createdDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));

            if (lastModifiedDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("lastModifiedDate"), lastModifiedDateFrom));
            if (lastModifiedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("lastModifiedDate"), lastModifiedDateTo));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTestReport> page = reportRepository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestReportResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestReportResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/radiology/reports/by-test/{orderTestId}")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> getByOrderTestId(@PathVariable Long orderTestId) {
        LOG.debug("REST get report by orderTestId={}", orderTestId);

        requireRadiologyTest(orderTestId);

        DiagnosticOrderTestReport report = reportService.getByOrderTestId(orderTestId);
        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(report));
    }

    @PostMapping("/radiology/reports")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> create(@Valid @RequestBody DiagnosticOrderTestReportCreateDTO dto) {
        LOG.debug("REST create report payload={}", dto);

        DiagnosticOrderTest test = requireRadiologyTest(dto.orderTestId());

        if (!test.getOrderId().equals(dto.orderId())) {
            throw new BadRequestAlertException("mismatch", "diagnostic_order_tests_report", "orderId does not match test.orderId");
        }

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException("not_accepted", "diagnostic_order_tests", "Radiology test must be ACCEPTED before creating report");
        }

        reportRepository.findByOrderIdAndOrderTestId(dto.orderId(), dto.orderTestId())
                .ifPresent(r -> {
                    throw new BadRequestAlertException(
                            "already_exists",
                            "diagnostic_order_tests_report",
                            "Report already exists for orderId=" + dto.orderId() + " orderTestId=" + dto.orderTestId()
                    );
                });

        DiagnosticOrderTestReport saved = reportService.create(dto);

        if (dto.report() != null && !dto.report().isBlank()) {
            diagnosticOrderTestStatusService.markReady(dto.orderTestId());
        }

        return ResponseEntity
                .created(URI.create("/api/patient/radiology/reports/" + saved.getId()))
                .body(DiagnosticOrderTestReportResponseVM.ofEntity(saved));
    }

    @PutMapping("/radiology/reports/{reportId}")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> update(
            @PathVariable Long reportId,
            @Valid @RequestBody DiagnosticOrderTestReportUpdateDTO dto
    ) {
        LOG.debug("REST update report id={} payload={}", reportId, dto);

        DiagnosticOrderTestReport updated = reportService.update(reportId, dto);

        if (dto.report() != null && !dto.report().isBlank()) {
            DiagnosticOrderTest test = requireRadiologyTest(updated.getOrderTestId());
            if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED && test.getProcessingStatus() != DiagnosticStatus.RESULT_READY) {
                throw new BadRequestAlertException("invalid_state", "diagnostic_order_tests", "Test must be ACCEPTED to mark RESULT_READY");
            }
            diagnosticOrderTestStatusService.markReady(updated.getOrderTestId());
        }

        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/review")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> review(@Valid @RequestBody DiagnosticOrderTestReportReviewDTO dto) {
        LOG.debug("REST review report payload={}", dto);

        requireRadiologyTest(dto.orderTestId());

        DiagnosticOrderTestReport updated = reportService.review(dto);
        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/reject")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> reject(@Valid @RequestBody DiagnosticOrderTestReportRejectDTO dto) {
        LOG.debug("REST reject report payload={}", dto);

        requireRadiologyTest(dto.orderTestId());

        DiagnosticOrderTestReport updated = reportService.reject(dto);
        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    /**
     * Starts radiology imaging for a test.
     * <p>
     * Prerequisite:
     * <ul>
     *   <li>Test must be RADIOLOGY and processingStatus must be ACCEPTED.</li>
     * </ul>
     */
    @PostMapping("/radiology/reports/image/{testId}/start")
    public ResponseEntity<RadiologyImageStatusResponseVM> startImage(@PathVariable Long testId) {
        LOG.debug("REST start image testId={}", testId);

        DiagnosticOrderTest test = requireRadiologyTest(testId);

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException("not_accepted", "diagnostic_order_tests", "Radiology test must be ACCEPTED before starting imaging");
        }

        return ResponseEntity.ok(reportService.startImage(test.getOrderId(), test.getId()));
    }

    @PostMapping("/radiology/reports/image/{testId}/pause")
    public ResponseEntity<RadiologyImageStatusResponseVM> pauseImage(@PathVariable Long testId) {
        LOG.debug("REST pause image testId={}", testId);

        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException("notfound", "diagnostic_order_tests_report", "Report not found for orderTestId " + testId));

        return ResponseEntity.ok(reportService.pauseImage(testId));
    }

    @PostMapping("/radiology/reports/image/{testId}/resume")
    public ResponseEntity<RadiologyImageStatusResponseVM> resumeImage(@PathVariable Long testId) {
        LOG.debug("REST resume image testId={}", testId);

        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException("notfound", "diagnostic_order_tests_report", "Report not found for orderTestId " + testId));

        return ResponseEntity.ok(reportService.resumeImage(testId));
    }

    /**
     * Finishes imaging.
     * <p>
     * Effects:
     * <ul>
     *   <li>Report imageStatus becomes FINISHED.</li>
     *   <li>Report processingStatus becomes RESULT_READY.</li>
     *   <li>DiagnosticOrderTest processingStatus becomes RESULT_READY.</li>
     * </ul>
     */
    @PostMapping("/radiology/reports/image/{testId}/finish")
    public ResponseEntity<RadiologyImageStatusResponseVM> finishImage(@PathVariable Long testId) {
        LOG.debug("REST finish image testId={}", testId);

        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException("notfound", "diagnostic_order_tests_report", "Report not found for orderTestId " + testId));

        RadiologyImageStatusResponseVM vm = reportService.finishImage(testId);

        diagnosticOrderTestStatusService.markReady(testId);

        return ResponseEntity.ok(vm);
    }

    private DiagnosticOrderTest requireRadiologyTest(Long testId) {
        return testRepository.findById(testId)
                .filter(t -> t.getOrderType() == TestType.RADIOLOGY)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound_or_not_radiology",
                        "diagnostic_order_tests",
                        "Radiology DiagnosticOrderTest not found with id " + testId
                ));
    }
}
