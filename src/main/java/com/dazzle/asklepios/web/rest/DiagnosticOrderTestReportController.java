package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportImageStatusLogRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestReportService;
import com.dazzle.asklepios.service.DiagnosticOrderTestStatusService;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportImageStatusLogResponseVM;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportResponseVM;
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
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
    private final DiagnosticOrderTestReportImageStatusLogRepository logRepository;

    public DiagnosticOrderTestReportController(
            DiagnosticOrderTestReportService reportService,
            DiagnosticOrderTestRepository testRepository,
            DiagnosticOrderTestReportRepository reportRepository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService, DiagnosticOrderTestReportImageStatusLogRepository logRepository
    ) {
        this.reportService = reportService;
        this.testRepository = testRepository;
        this.reportRepository = reportRepository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.logRepository = logRepository;
    }


    /**
     * Filters radiology diagnostic order test reports using dynamic criteria.
     *
     * <p>
     * This endpoint returns records from {@code diagnostic_order_tests_report}
     * and applies additional filters related to the parent order and patient
     * only when explicitly requested.
     * </p>
     *
     * <h3>Filtering behavior</h3>
     * <ul>
     *   <li>All report-level fields (status, dates, severity, etc.) are filtered
     *       directly on {@code diagnostic_order_tests_report}.</li>
     *
     *   <li>{@code fromDepartmentIn} is applied on the parent
     *       {@code diagnostic_orders.from_department_id}.</li>
     *
     *   <li>Patient-related filters ({@code mrn}, {@code patientName}) are applied
     *       <strong>only</strong> when provided. If neither is present, the query
     *       does <strong>not</strong> require a matching patient record.</li>
     *
     *   <li>The query does not enforce any filtering by test type. It returns all
     *       reports matching the provided criteria regardless of LAB/RADIOLOGY type.</li>
     * </ul>
     *
     * <h3>Mutual exclusivity rules</h3>
     * <ul>
     *   <li>Use either {@code processingStatus} or {@code processingStatusIn}, not both.</li>
     *   <li>Use either {@code imageStatus} or {@code imageStatusIn}, not both.</li>
     * </ul>
     *
     * <h3>Notes</h3>
     * <ul>
     *   <li>If only {@code fromDepartmentIn} is provided, reports are returned even
     *       when the parent order has no resolvable patient record.</li>
     *
     *   <li>Pagination is applied using standard Spring Data mechanisms.</li>
     * </ul>
     *
     * @param id optional report id
     * @param orderId optional parent order id
     * @param orderTestId optional diagnostic order test id
     * @param severity optional severity value
     * @param approvedBy optional approver username
     * @param rejectedBy optional rejector username
     * @param reviewBy optional reviewer username
     * @param approvedDateFrom optional approved date lower bound (inclusive)
     * @param approvedDateTo optional approved date upper bound (inclusive)
     * @param rejectedDateFrom optional rejected date lower bound (inclusive)
     * @param rejectedDateTo optional rejected date upper bound (inclusive)
     * @param reviewDateFrom optional review date lower bound (inclusive)
     * @param reviewDateTo optional review date upper bound (inclusive)
     * @param processingStatusIn optional list of allowed processing statuses
     * @param processingStatusNotIn optional list of excluded processing statuses
     * @param imageStatusIn optional list of allowed image statuses
     * @param imageStatusNotIn optional list of excluded image statuses
     * @param createdDateFrom optional created date lower bound (inclusive)
     * @param createdDateTo optional created date upper bound (inclusive)
     * @param lastModifiedDateFrom optional last modified date lower bound (inclusive)
     * @param lastModifiedDateTo optional last modified date upper bound (inclusive)
     * @param fromDepartmentIn optional list of originating department ids
     * @param patientName optional patient name (partial, case-insensitive)
     * @param mrn optional medical record number (partial, case-insensitive)
     * @param pageable pagination and sorting information
     *
     * @return paged list of {@link DiagnosticOrderTestReportResponseVM}
     *
     */
    @GetMapping("/radiology/reports")
    public ResponseEntity<List<DiagnosticOrderTestReportResponseVM>> filterReports(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "orderTestId", required = false) Long orderTestId,

            @RequestParam(name = "severity", required = false) String severity,

            @RequestParam(name = "approvedBy", required = false) String approvedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,
            @RequestParam(name = "reviewBy", required = false) String reviewBy,

            @RequestParam(name = "approvedDateFrom", required = false) Instant approvedDateFrom,
            @RequestParam(name = "approvedDateTo", required = false) Instant approvedDateTo,
            @RequestParam(name = "rejectedDateFrom", required = false) Instant rejectedDateFrom,
            @RequestParam(name = "rejectedDateTo", required = false) Instant rejectedDateTo,
            @RequestParam(name = "reviewDateFrom", required = false) Instant reviewDateFrom,
            @RequestParam(name = "reviewDateTo", required = false) Instant reviewDateTo,

            @RequestParam(name = "processingStatusIn", required = false) List<DiagnosticStatus> processingStatusIn,
            @RequestParam(name = "processingStatusNotIn", required = false) List<DiagnosticStatus> processingStatusNotIn,

            @RequestParam(name = "imageStatusIn", required = false) List<RadiologyImageStatus> imageStatusIn,
            @RequestParam(name = "imageStatusNotIn", required = false) List<RadiologyImageStatus> imageStatusNotIn,

            @RequestParam(name = "createdDateFrom", required = false) Instant createdDateFrom,
            @RequestParam(name = "createdDateTo", required = false) Instant createdDateTo,
            @RequestParam(name = "lastModifiedDateFrom", required = false) Instant lastModifiedDateFrom,
            @RequestParam(name = "lastModifiedDateTo", required = false) Instant lastModifiedDateTo,

            @RequestParam(name = "fromDepartmentIn", required = false) List<Long> fromDepartmentIn,
            @RequestParam(name = "patientName", required = false) String patientName,
            @RequestParam(name = "mrn", required = false) String mrn,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST filter reports orderId={} orderTestId={} fromDepartmentIn={} patientName={} mrn={}",
                orderId, orderTestId, fromDepartmentIn, patientName, mrn
        );

        Specification<DiagnosticOrderTestReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (id != null) predicates.add(cb.equal(root.get("id"), id));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (orderTestId != null) predicates.add(cb.equal(root.get("orderTestId"), orderTestId));

            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }

            if (approvedBy != null && !approvedBy.isBlank())
                predicates.add(cb.equal(root.get("approvedBy"), approvedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                predicates.add(cb.equal(root.get("rejectedBy"), rejectedBy));
            if (reviewBy != null && !reviewBy.isBlank()) predicates.add(cb.equal(root.get("reviewBy"), reviewBy));

            if (approvedDateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("approvedDate"), approvedDateFrom));
            if (approvedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("approvedDate"), approvedDateTo));

            if (rejectedDateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("rejectedDate"), rejectedDateFrom));
            if (rejectedDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("rejectedDate"), rejectedDateTo));

            if (reviewDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("reviewDate"), reviewDateFrom));
            if (reviewDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("reviewDate"), reviewDateTo));

            if (processingStatusIn != null && !processingStatusIn.isEmpty())
                predicates.add(root.get("processingStatus").in(processingStatusIn));
            if (processingStatusNotIn != null && !processingStatusNotIn.isEmpty())
                predicates.add(cb.not(root.get("processingStatus").in(processingStatusNotIn)));

            if (imageStatusIn != null && !imageStatusIn.isEmpty())
                predicates.add(root.get("imageStatus").in(imageStatusIn));
            if (imageStatusNotIn != null && !imageStatusNotIn.isEmpty())
                predicates.add(cb.not(root.get("imageStatus").in(imageStatusNotIn)));

            if (createdDateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            if (createdDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));

            if (lastModifiedDateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastModifiedDate"), lastModifiedDateFrom));
            if (lastModifiedDateTo != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("lastModifiedDate"), lastModifiedDateTo));

            boolean needOrderFilter = (fromDepartmentIn != null && !fromDepartmentIn.isEmpty());

            boolean needPatientFilter =
                    (patientName != null && !patientName.isBlank()) ||
                            (mrn != null && !mrn.isBlank());

            boolean needOrderPatientSubquery = needOrderFilter || needPatientFilter;

            if (needOrderPatientSubquery) {
                jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<DiagnosticOrder> orderRoot = subquery.from(DiagnosticOrder.class);

                List<Predicate> subPredicates = new ArrayList<>();

                subPredicates.add(cb.equal(orderRoot.get("id"), root.get("orderId")));


                if (needOrderFilter) {
                    subPredicates.add(orderRoot.get("fromDepartmentId").in(fromDepartmentIn));
                }

                if (needPatientFilter) {
                    jakarta.persistence.criteria.Root<Patient> patientRoot = subquery.from(Patient.class);

                    subPredicates.add(cb.equal(patientRoot.get("id"), orderRoot.get("patientId")));

                    if (mrn != null && !mrn.isBlank()) {
                        subPredicates.add(cb.like(
                                cb.lower(patientRoot.get("medicalRecordNumber")),
                                "%" + mrn.trim().toLowerCase() + "%"
                        ));
                    }

                    if (patientName != null && !patientName.isBlank()) {
                        String like = "%" + patientName.trim().toLowerCase() + "%";
                        subPredicates.add(cb.or(
                                cb.like(cb.lower(patientRoot.get("firstName")), like),
                                cb.like(cb.lower(patientRoot.get("secondName")), like),
                                cb.like(cb.lower(patientRoot.get("thirdName")), like),
                                cb.like(cb.lower(patientRoot.get("lastName")), like)
                        ));
                    }
                }

                subquery.select(orderRoot.get("id"))
                        .where(subPredicates.toArray(new Predicate[0]));

                predicates.add(cb.exists(subquery));
            }

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


        if (dto.report() != null) {
            if (updated.getImageStatus() != RadiologyImageStatus.FINISHED) {
                throw new BadRequestAlertException(
                        "invalid_state",
                        "diagnostic_order_tests_report",
                        "Cannot write report before image  is FINISHED"
                );
            }

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

    /**
     * Approves radiology report.
     * Effects:
     * - DiagnosticOrderTestReport.processingStatus -> RESULT_APPROVED
     * - fills approvedBy/approvedDate
     * - DiagnosticOrderTest.processingStatus -> RESULT_APPROVED (via DiagnosticOrderTestStatusService inside service)
     */
    @PostMapping("/radiology/reports/{reportId}/approve")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> approve(@PathVariable Long reportId) {
        LOG.debug("REST approve report reportId={}", reportId);

        DiagnosticOrderTestReport updated = reportService.approve(reportId);
        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
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

    @PostMapping("/radiology/reports/{reportId}/second-approve")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> secondApprove(@PathVariable Long reportId) {
        LOG.debug("REST second approve report reportId={}", reportId);

        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        DiagnosticOrderTestReport updated = reportService.secondApprove(report);
        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    /**
     * GET /{reportId}/image-status-log : Get image status log rows for a report.
     *
     * @param reportId report id
     * @return list of log rows (empty if none)
     */
    @GetMapping("/radiology/reports/{reportId}/image-status-log")
    public ResponseEntity<List<DiagnosticOrderTestReportImageStatusLogResponseVM>> getByReportId(
            @PathVariable Long reportId
    ) {
        LOG.debug("REST get image status log by reportId={}", reportId);

        List<DiagnosticOrderTestReportImageStatusLogResponseVM> body = logRepository
                .findByReportIdOrderByStatusDateDesc(reportId)
                .stream()
                .map(DiagnosticOrderTestReportImageStatusLogResponseVM::ofEntity)
                .toList();

        LOG.info("REST image status log rows={} for reportId={}", body.size(), reportId);
        return ResponseEntity.ok(body);
    }
}
