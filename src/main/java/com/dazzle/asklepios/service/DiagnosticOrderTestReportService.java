package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for managing {@link DiagnosticOrderTestReport}.
 *
 * <p>This service owns all validations and persistence access for radiology reports:
 * test constraints (existence/type/order match), prerequisites (ACCEPTED), uniqueness,
 * and image workflow requirements.</p>
 */
@Service
@Transactional
public class DiagnosticOrderTestReportService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportService.class);

    private final DiagnosticOrderTestReportRepository reportRepository;
    private final DiagnosticOrderTestRepository testRepository;


    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    public DiagnosticOrderTestReportService(
            DiagnosticOrderTestReportRepository reportRepository,
            DiagnosticOrderTestRepository testRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService
    ) {
        this.reportRepository = reportRepository;
        this.testRepository = testRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests_report",
                        "No authenticated user"
                ));
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

    private void recomputeOrderStatusesByOrderTestId(Long orderTestId) {
        DiagnosticOrderTest test = testRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));
        diagnosticOrderStatusService.recomputeLabRadStatuses(test.getOrderId());
    }

    @Transactional(readOnly = true)
    public DiagnosticOrderTestReport getByOrderTestIdForRadiology(Long orderTestId) {
        LOG.debug("[DiagnosticOrderTestReportService] GET_BY_ORDER_TEST_ID - start. orderTestId={}", orderTestId);
        requireRadiologyTest(orderTestId);
        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + orderTestId
                ));
        LOG.debug("[DiagnosticOrderTestReportService] GET_BY_ORDER_TEST_ID - done. reportId={} orderTestId={}",
                report.getId(), report.getOrderTestId());
        return report;
    }

    public DiagnosticOrderTestReport createRadiologyReport(DiagnosticOrderTestReportCreateDTO dto) {
        LOG.debug("[DiagnosticOrderTestReportService] CREATE_RADIOLOGY_REPORT - start. payload={}", dto);
        DiagnosticOrderTest test = requireRadiologyTest(dto.orderTestId());


        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException("not_accepted", "diagnostic_order_tests",
                    "Radiology test must be ACCEPTED before creating report");
        }

        reportRepository.findByOrderTestId(dto.orderTestId())
                .ifPresent(existing -> {
                    throw new BadRequestAlertException(
                            "already_exists",
                            "diagnostic_order_tests_report",
                            "Report already exists for this Test=" + dto.orderTestId()
                    );
                });

        DiagnosticOrderTestReport report = DiagnosticOrderTestReport.builder()
                .orderTestId(dto.orderTestId())
                .report(dto.report())
                .severity(dto.severity())
                .processingStatus(DiagnosticStatus.NEW)
                .build();

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        if (dto.report() != null && !dto.report().isBlank()) {
            diagnosticOrderTestStatusService.markReady(dto.orderTestId());
        }

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] CREATE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} processingStatus={} imageStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProcessingStatus(), saved.getImageStatus());
        return saved;
    }

    public DiagnosticOrderTestReport updateRadiologyReport(Long reportId, DiagnosticOrderTestReportUpdateDTO dto) {
        LOG.debug("[DiagnosticOrderTestReportService] UPDATE_RADIOLOGY_REPORT - start. reportId={} payload={}", reportId, dto);
        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        if (dto.report() != null) {
            if (report.getImageStatus() != RadiologyImageStatus.FINISHED) {
                throw new BadRequestAlertException(
                        "invalid_state",
                        "diagnostic_order_tests_report",
                        "Cannot write report before image is FINISHED"
                );
            }
        }

        report.setReport(dto.report());
        report.setSeverity(dto.severity());
        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] UPDATE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} severity={}",
                saved.getId(), saved.getOrderTestId(), saved.getSeverity());
        return saved;
    }

    public DiagnosticOrderTestReport reviewRadiologyReport(DiagnosticOrderTestReportReviewDTO dto) {
        LOG.debug("[DiagnosticOrderTestReportService] REVIEW_RADIOLOGY_REPORT - start. payload={}", dto);
        requireRadiologyTest(dto.orderTestId());

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        report.setReviewBy(currentUsername());
        report.setReviewDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] REVIEW_RADIOLOGY_REPORT - done. reportId={} orderTestId={} reviewBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getReviewBy());
        return saved;
    }

    public DiagnosticOrderTestReport rejectRadiologyReport(DiagnosticOrderTestReportRejectDTO dto) {
        LOG.debug("[DiagnosticOrderTestReportService] REJECT_RADIOLOGY_REPORT - start. payload={}", dto);
        requireRadiologyTest(dto.orderTestId());

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        report.setRejectedBy(currentUsername());
        report.setRejectedReason(dto.rejectedReason());
        report.setRejectedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REJECTED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] REJECT_RADIOLOGY_REPORT - done. reportId={} orderTestId={} rejectedBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getRejectedBy());
        return saved;
    }

    public RadiologyImageStatusResponseVM startRadiologyImage(Long testId) {
        LOG.debug("[DiagnosticOrderTestReportService] START_RADIOLOGY_IMAGE - start. testId={}", testId);
        DiagnosticOrderTest test = requireRadiologyTest(testId);

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException(
                    "not_accepted",
                    "diagnostic_order_tests",
                    "Radiology test must be ACCEPTED before starting imaging"
            );
        }

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(testId)
                .orElseGet(() -> DiagnosticOrderTestReport.builder()
                        .orderTestId(testId)
                        .processingStatus(DiagnosticStatus.NEW)
                        .build());

        ensureImageTransitionAllowed(report.getImageStatus(), RadiologyImageStatus.STARTED);

        report.setImageStatus(RadiologyImageStatus.STARTED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        RadiologyImageStatusResponseVM response = new RadiologyImageStatusResponseVM(
                saved.getId(),
                saved.getOrderTestId(),
                saved.getImageStatus(),
                saved.getLastModifiedDate()
        );
        LOG.debug("[DiagnosticOrderTestReportService] START_RADIOLOGY_IMAGE - done. reportId={} orderTestId={} imageStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getImageStatus());
        return response;
    }

    public RadiologyImageStatusResponseVM pauseRadiologyImage(Long testId) {
        LOG.debug("[DiagnosticOrderTestReportService] PAUSE_RADIOLOGY_IMAGE - start. testId={}", testId);
        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + testId
                ));

        RadiologyImageStatusResponseVM response = setImageStatusByReport(testId, RadiologyImageStatus.PAUSED);
        LOG.debug("[DiagnosticOrderTestReportService] PAUSE_RADIOLOGY_IMAGE - done. testId={} imageStatus={}",
                testId, response.imageStatus());
        return response;
    }

    public RadiologyImageStatusResponseVM resumeRadiologyImage(Long testId) {
        LOG.debug("[DiagnosticOrderTestReportService] RESUME_RADIOLOGY_IMAGE - start. testId={}", testId);
        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + testId
                ));

        RadiologyImageStatusResponseVM response = setImageStatusByReport(testId, RadiologyImageStatus.RESUMED);
        LOG.debug("[DiagnosticOrderTestReportService] RESUME_RADIOLOGY_IMAGE - done. testId={} imageStatus={}",
                testId, response.imageStatus());
        return response;
    }

    public RadiologyImageStatusResponseVM finishRadiologyImage(Long testId) {
        LOG.debug("[DiagnosticOrderTestReportService] FINISH_RADIOLOGY_IMAGE - start. testId={}", testId);
        requireRadiologyTest(testId);

        reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + testId
                ));

        RadiologyImageStatusResponseVM vm = setImageStatusByReport(testId, RadiologyImageStatus.FINISHED);

        diagnosticOrderTestStatusService.markReady(testId);

        LOG.debug("[DiagnosticOrderTestReportService] FINISH_RADIOLOGY_IMAGE - done. testId={} imageStatus={}",
                testId, vm.imageStatus());
        return vm;
    }

    public DiagnosticOrderTestReport approveRadiologyReport(Long reportId) {
        LOG.debug("[DiagnosticOrderTestReportService] APPROVE_RADIOLOGY_REPORT - start. reportId={}", reportId);
        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        report.setApprovedBy(currentUsername());
        report.setApprovedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        diagnosticOrderTestStatusService.approve(saved.getOrderTestId());
        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] APPROVE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} approvedBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getApprovedBy());
        return saved;
    }

    public DiagnosticOrderTestReport secondApproveRadiologyReport(Long reportId) {
        LOG.debug("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - start. reportId={}", reportId);
        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        if (report.getApprovedBy() == null || report.getApprovedDate() == null) {
            throw new BadRequestAlertException(
                    "first_approve_required",
                    "diagnostic_order_tests_report",
                    "First approve is required before second approve"
            );
        }

        if (report.getSecondApprovedBy() != null || report.getSecondApprovedDate() != null) {
            throw new BadRequestAlertException(
                    "already_second_approved",
                    "diagnostic_order_tests_report",
                    "Report already second approved"
            );
        }

        String currentUser = currentUsername();

        if (currentUser.equals(report.getApprovedBy())) {
            throw new BadRequestAlertException(
                    "same_user_not_allowed",
                    "diagnostic_order_tests_report",
                    "Second approve must be performed by a different user"
            );
        }

        report.setSecondApprovedBy(currentUser);
        report.setSecondApprovedDate(Instant.now());

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} secondApprovedBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getSecondApprovedBy());
        return saved;
    }

    private RadiologyImageStatusResponseVM setImageStatusByReport(Long testId, RadiologyImageStatus to) {
        LOG.debug("[DiagnosticOrderTestReportService] SET_IMAGE_STATUS - start. testId={} to={}", testId, to);
        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + testId
                ));

        ensureImageTransitionAllowed(report.getImageStatus(), to);

        report.setImageStatus(to);

        if (to == RadiologyImageStatus.FINISHED) {
            report.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        }

        DiagnosticOrderTestReport saved = reportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        RadiologyImageStatusResponseVM response = new RadiologyImageStatusResponseVM(
                saved.getId(),
                saved.getOrderTestId(),
                saved.getImageStatus(),
                saved.getLastModifiedDate()
        );

        LOG.debug("[DiagnosticOrderTestReportService] SET_IMAGE_STATUS - done. reportId={} orderTestId={} imageStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getImageStatus());
        return response;
    }

    private void ensureImageTransitionAllowed(RadiologyImageStatus from, RadiologyImageStatus to) {
        LOG.debug("[DiagnosticOrderTestReportService] ENSURE_IMAGE_TRANSITION_ALLOWED - from={} to={}", from, to);
        if (to == RadiologyImageStatus.STARTED) {
            if (from == null) return;
            if (from == RadiologyImageStatus.STARTED || from == RadiologyImageStatus.RESUMED || from == RadiologyImageStatus.PAUSED)
                return;
            if (from == RadiologyImageStatus.FINISHED) throw invalidImageTransition(from, to);
            return;
        }

        if (from == null) {
            throw new BadRequestAlertException("missing_state", "diagnostic_order_tests_report", "Image status is missing");
        }

        if (from == RadiologyImageStatus.FINISHED) {
            throw new BadRequestAlertException("invalid_transition", "diagnostic_order_tests_report", "Image workflow already finished");
        }

        boolean ok =
                (from == RadiologyImageStatus.STARTED && (to == RadiologyImageStatus.PAUSED || to == RadiologyImageStatus.FINISHED)) ||
                        (from == RadiologyImageStatus.PAUSED && to == RadiologyImageStatus.RESUMED) ||
                        (from == RadiologyImageStatus.RESUMED && (to == RadiologyImageStatus.PAUSED || to == RadiologyImageStatus.FINISHED));

        if (!ok) throw invalidImageTransition(from, to);
    }

    private BadRequestAlertException invalidImageTransition(RadiologyImageStatus from, RadiologyImageStatus to) {
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests_report",
                "Invalid transition " + from + " -> " + to
        );
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestReport> filterReports(
            Long id,
            List<Long> orderIdIn,
            Long orderTestId,
            String severity,
            String approvedBy,
            String rejectedBy,
            String reviewBy,
            Boolean reviewed,
            Instant approvedDateFrom,
            Instant approvedDateTo,
            Instant rejectedDateFrom,
            Instant rejectedDateTo,
            Instant reviewDateFrom,
            Instant reviewDateTo,
            List<DiagnosticStatus> processingStatusIn,
            List<DiagnosticStatus> processingStatusNotIn,
            List<RadiologyImageStatus> imageStatusIn,
            List<RadiologyImageStatus> imageStatusNotIn,
            Instant createdDateFrom,
            Instant createdDateTo,
            Instant lastModifiedDateFrom,
            Instant lastModifiedDateTo,
            List<Long> fromDepartmentIn,
            String patientName,
            String mrn,
            Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTestReportService] FILTER_REPORTS - start. id={} orderIdIn={} orderTestId={} severity={} approvedBy={} rejectedBy={} reviewBy={} reviewed={} pageable={}",
                id, orderIdIn, orderTestId, severity, approvedBy, rejectedBy, reviewBy, reviewed, pageable);
        Specification<DiagnosticOrderTestReport> spec = (reportRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (reviewed != null) {
                predicates.add(reviewed ? criteriaBuilder.isNotNull(reportRoot.get("reviewDate")) : criteriaBuilder.isNull(reportRoot.get("reviewDate")));
            }

            if (id != null) predicates.add(criteriaBuilder.equal(reportRoot.get("id"), id));

            if (orderIdIn != null && !orderIdIn.isEmpty()) {
                predicates.add(reportRoot.get("orderId").in(orderIdIn));
            }

            if (orderTestId != null) predicates.add(criteriaBuilder.equal(reportRoot.get("orderTestId"), orderTestId));

            if (severity != null && !severity.isBlank())
                predicates.add(criteriaBuilder.equal(reportRoot.get("severity"), severity));

            if (approvedBy != null && !approvedBy.isBlank())
                predicates.add(criteriaBuilder.equal(reportRoot.get("approvedBy"), approvedBy));
            if (rejectedBy != null && !rejectedBy.isBlank())
                predicates.add(criteriaBuilder.equal(reportRoot.get("rejectedBy"), rejectedBy));
            if (reviewBy != null && !reviewBy.isBlank())
                predicates.add(criteriaBuilder.equal(reportRoot.get("reviewBy"), reviewBy));

            if (approvedDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("approvedDate"), approvedDateFrom));
            if (approvedDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("approvedDate"), approvedDateTo));

            if (rejectedDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("rejectedDate"), rejectedDateFrom));
            if (rejectedDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("rejectedDate"), rejectedDateTo));

            if (reviewDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("reviewDate"), reviewDateFrom));
            if (reviewDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("reviewDate"), reviewDateTo));

            if (processingStatusIn != null && !processingStatusIn.isEmpty())
                predicates.add(reportRoot.get("processingStatus").in(processingStatusIn));
            if (processingStatusNotIn != null && !processingStatusNotIn.isEmpty())
                predicates.add(criteriaBuilder.not(reportRoot.get("processingStatus").in(processingStatusNotIn)));

            if (imageStatusIn != null && !imageStatusIn.isEmpty())
                predicates.add(reportRoot.get("imageStatus").in(imageStatusIn));
            if (imageStatusNotIn != null && !imageStatusNotIn.isEmpty())
                predicates.add(criteriaBuilder.not(reportRoot.get("imageStatus").in(imageStatusNotIn)));

            if (createdDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("createdDate"), createdDateFrom));
            if (createdDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("createdDate"), createdDateTo));

            if (lastModifiedDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("lastModifiedDate"), lastModifiedDateFrom));
            if (lastModifiedDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("lastModifiedDate"), lastModifiedDateTo));

            boolean needOrderFilter = (fromDepartmentIn != null && !fromDepartmentIn.isEmpty());
            boolean needPatientFilter = (patientName != null && !patientName.isBlank()) || (mrn != null && !mrn.isBlank());
            boolean needOrderIdFilter = (orderIdIn != null && !orderIdIn.isEmpty());

            boolean needSubquery = needOrderFilter || needPatientFilter || needOrderIdFilter;

            if (needSubquery) {
                var subquery = criteriaQuery.subquery(Long.class);

                var testRoot = subquery.from(DiagnosticOrderTest.class);
                var orderRoot = subquery.from(DiagnosticOrder.class);

                List<Predicate> subPredicates = new ArrayList<>();

                subPredicates.add(criteriaBuilder.equal(testRoot.get("id"), reportRoot.get("orderTestId")));

                subPredicates.add(criteriaBuilder.equal(orderRoot.get("id"), testRoot.get("orderId")));

                if (needOrderIdFilter) {
                    subPredicates.add(orderRoot.get("id").in(orderIdIn));
                }

                // fromDepartmentIn filter
                if (needOrderFilter) {
                    subPredicates.add(orderRoot.get("fromDepartmentId").in(fromDepartmentIn));
                }

                // patient filters
                if (needPatientFilter) {
                    var patientRoot = subquery.from(Patient.class);
                    subPredicates.add(criteriaBuilder.equal(patientRoot.get("id"), orderRoot.get("patientId")));

                    if (mrn != null && !mrn.isBlank()) {
                        subPredicates.add(criteriaBuilder.like(
                                criteriaBuilder.lower(patientRoot.get("medicalRecordNumber")),
                                "%" + mrn.trim().toLowerCase() + "%"
                        ));
                    }

                    if (patientName != null && !patientName.isBlank()) {
                        String like = "%" + patientName.trim().toLowerCase() + "%";
                        subPredicates.add(criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(patientRoot.get("firstName")), like),
                                criteriaBuilder.like(criteriaBuilder.lower(patientRoot.get("secondName")), like),
                                criteriaBuilder.like(criteriaBuilder.lower(patientRoot.get("thirdName")), like),
                                criteriaBuilder.like(criteriaBuilder.lower(patientRoot.get("lastName")), like)
                        ));
                    }
                }

                subquery.select(orderRoot.get("id"))
                        .where(subPredicates.toArray(new Predicate[0]));

                predicates.add(criteriaBuilder.exists(subquery));
            }


            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTestReport> page = reportRepository.findAll(spec, pageable);
        LOG.debug("[DiagnosticOrderTestReportService] FILTER_REPORTS - done. returned={} totalElements={} totalPages={}",
                page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return page;
    }
}
