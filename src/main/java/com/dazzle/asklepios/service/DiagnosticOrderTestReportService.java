package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportCommentsRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportResponseVM;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;


    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestReportCommentsRepository diagnosticOrderTestReportCommentsRepository;

    public DiagnosticOrderTestReportService(
            DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService, DiagnosticOrderTestReportCommentsRepository diagnosticOrderTestReportCommentsRepository
    ) {
        this.diagnosticOrderTestReportRepository = diagnosticOrderTestReportRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestReportCommentsRepository = diagnosticOrderTestReportCommentsRepository;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            LOG.warn("[DiagnosticOrderTestReportService] AUTH - unauthenticated request");
            throw new BadRequestAlertException(
                    "unauthenticated",
                    "diagnostic_order_tests_report",
                    "No authenticated user"
            );
        }
        return username;
    }

    private DiagnosticOrderTest requireRadiologyTest(Long testId) {
        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> {
                    LOG.warn("[DiagnosticOrderTestReportService] REQUIRE_RADIOLOGY_TEST - test not found. testId={}", testId);
                    return new BadRequestAlertException(
                            "notfound_or_not_radiology",
                            "diagnostic_order_tests",
                            "Radiology DiagnosticOrderTest not found with id " + testId
                    );
                });

        if (test.getOrderType() != TestType.RADIOLOGY) {
            LOG.warn("[DiagnosticOrderTestReportService] REQUIRE_RADIOLOGY_TEST - test is not radiology. testId={} orderType={}",
                    testId, test.getOrderType());
            throw new BadRequestAlertException(
                    "notfound_or_not_radiology",
                    "diagnostic_order_tests",
                    "Radiology DiagnosticOrderTest not found with id " + testId
            );
        }

        LOG.debug("[DiagnosticOrderTestReportService] REQUIRE_RADIOLOGY_TEST - validated. testId={} orderId={} processingStatus={}",
                test.getId(), test.getOrderId(), test.getProcessingStatus());
        return test;
    }

    private void recomputeOrderStatusesByOrderTestId(Long orderTestId) {
        LOG.debug("[DiagnosticOrderTestReportService] RECOMPUTE_ORDER_STATUS - start. orderTestId={}", orderTestId);
        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));
        diagnosticOrderStatusService.recomputeLabRadStatuses(test.getOrderId());
        LOG.debug("[DiagnosticOrderTestReportService] RECOMPUTE_ORDER_STATUS - done. orderTestId={} orderId={}",
                orderTestId, test.getOrderId());
    }

    @Transactional(readOnly = true)
    public Optional<DiagnosticOrderTestReport> findByOrderTestIdForRadiology(Long orderTestId) {
        LOG.debug("[DiagnosticOrderTestReportService] GET_BY_ORDER_TEST_ID - start. orderTestId={}", orderTestId);

        requireRadiologyTest(orderTestId);

        return diagnosticOrderTestReportRepository.findByOrderTestId(orderTestId);
    }
    public DiagnosticOrderTestReport createRadiologyReport(DiagnosticOrderTestReportCreateDTO reportCreateDTO) {
        LOG.debug("[DiagnosticOrderTestReportService] CREATE_RADIOLOGY_REPORT - start. payload={}", reportCreateDTO);
        DiagnosticOrderTest orderTest = requireRadiologyTest(reportCreateDTO.orderTestId());


        if (orderTest.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException("not_accepted", "diagnostic_order_tests",
                    "Radiology orderTest must be ACCEPTED before creating report");
        }

        diagnosticOrderTestReportRepository.findByOrderTestId(reportCreateDTO.orderTestId())
                .ifPresent(existing -> {
                    throw new BadRequestAlertException(
                            "already_exists",
                            "diagnostic_order_tests_report",
                            "Report already exists for this Test=" + reportCreateDTO.orderTestId()
                    );
                });

        DiagnosticOrderTestReport report = DiagnosticOrderTestReport.builder()
                .orderTestId(reportCreateDTO.orderTestId())
                .report(reportCreateDTO.report())
                .severity(reportCreateDTO.severity())
                .processingStatus(DiagnosticStatus.NEW)
                .build();

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

        if (reportCreateDTO.report() != null && !reportCreateDTO.report().isBlank()) {
            diagnosticOrderTestStatusService.markReady(reportCreateDTO.orderTestId());
        }

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] CREATE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} processingStatus={} imageStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProcessingStatus(), saved.getImageStatus());
        return saved;
    }

    public DiagnosticOrderTestReport updateRadiologyReport(Long reportId, DiagnosticOrderTestReportUpdateDTO orderTestReportUpdateDTO) {
        LOG.debug("[DiagnosticOrderTestReportService] UPDATE_RADIOLOGY_REPORT - start. reportId={} payload={}", reportId, orderTestReportUpdateDTO);
        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        if (orderTestReportUpdateDTO.report() != null) {
            if (report.getImageStatus() != RadiologyImageStatus.FINISHED) {
                LOG.warn("[DiagnosticOrderTestReportService] UPDATE_RADIOLOGY_REPORT - report text update blocked. reportId={} orderTestId={} imageStatus={}",
                        reportId, report.getOrderTestId(), report.getImageStatus());
                throw new BadRequestAlertException(
                        "invalid_state",
                        "diagnostic_order_tests_report",
                        "Cannot write report before image is FINISHED"
                );
            }
        }

        report.setReport(orderTestReportUpdateDTO.report());
        report.setSeverity(orderTestReportUpdateDTO.severity());
        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] UPDATE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} severity={}",
                saved.getId(), saved.getOrderTestId(), saved.getSeverity());
        return saved;
    }

    @Transactional
    public DiagnosticOrderTestReport reviewRadiologyReport(
            DiagnosticOrderTestReportReviewDTO orderTestReportReviewDTO) {

        LOG.debug("[DiagnosticOrderTestReportService] TOGGLE_REVIEW_RADIOLOGY_REPORT - start. payload={}",
                orderTestReportReviewDTO);

        requireRadiologyTest(orderTestReportReviewDTO.orderTestId());

        DiagnosticOrderTestReport report =
                diagnosticOrderTestReportRepository
                        .findByOrderTestId(orderTestReportReviewDTO.orderTestId())
                        .orElseThrow(() -> new BadRequestAlertException(
                                "notfound",
                                "diagnostic_order_tests_report",
                                "Report not found for orderTestId "
                                        + orderTestReportReviewDTO.orderTestId()
                        ));

        String currentUser = currentUsername();
        Instant now = Instant.now();

        if (report.getReviewDate() == null) {

            report.setReviewBy(currentUser);
            report.setReviewDate(now);

            LOG.debug("[DiagnosticOrderTestReportService] REPORT REVIEWED. orderTestId={} by={}",
                    report.getOrderTestId(), currentUser);

        } else {

            report.setReviewBy(null);
            report.setReviewDate(null);

            LOG.debug("[DiagnosticOrderTestReportService] REPORT UNREVIEWED. orderTestId={}",
                    report.getOrderTestId());
        }

        DiagnosticOrderTestReport saved =
                diagnosticOrderTestReportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] TOGGLE_REVIEW_RADIOLOGY_REPORT - done. reportId={} orderTestId={}",
                saved.getId(), saved.getOrderTestId());

        return saved;
    }
    public DiagnosticOrderTestReport rejectRadiologyReport(DiagnosticOrderTestReportRejectDTO orderTestReportRejectDTO) {
        LOG.debug("[DiagnosticOrderTestReportService] REJECT_RADIOLOGY_REPORT - start. payload={}", orderTestReportRejectDTO);
        requireRadiologyTest(orderTestReportRejectDTO.orderTestId());

        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findByOrderTestId(orderTestReportRejectDTO.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + orderTestReportRejectDTO.orderTestId()
                ));

        report.setRejectedBy(currentUsername());
        report.setRejectedReason(orderTestReportRejectDTO.rejectedReason());
        report.setRejectedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REJECTED);

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

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

        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findByOrderTestId(testId)
                .orElseGet(() -> DiagnosticOrderTestReport.builder()
                        .orderTestId(testId)
                        .processingStatus(DiagnosticStatus.NEW)
                        .build());

        ensureImageTransitionAllowed(report.getImageStatus(), RadiologyImageStatus.STARTED);

        report.setImageStatus(RadiologyImageStatus.STARTED);

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

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

        diagnosticOrderTestReportRepository.findByOrderTestId(testId)
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

        diagnosticOrderTestReportRepository.findByOrderTestId(testId)
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

        diagnosticOrderTestReportRepository.findByOrderTestId(testId)
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
        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        report.setApprovedBy(currentUsername());
        report.setApprovedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

        diagnosticOrderTestStatusService.approve(saved.getOrderTestId());
        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] APPROVE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} approvedBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getApprovedBy());
        return saved;
    }

    public DiagnosticOrderTestReport secondApproveRadiologyReport(Long reportId) {
        LOG.debug("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - start. reportId={}", reportId);
        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        requireRadiologyTest(report.getOrderTestId());

        if (report.getApprovedBy() == null || report.getApprovedDate() == null) {
            LOG.warn("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - first approval missing. reportId={} orderTestId={}",
                    reportId, report.getOrderTestId());
            throw new BadRequestAlertException(
                    "first_approve_required",
                    "diagnostic_order_tests_report",
                    "First approve is required before second approve"
            );
        }

        if (report.getSecondApprovedBy() != null || report.getSecondApprovedDate() != null) {
            LOG.warn("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - already second approved. reportId={} orderTestId={} secondApprovedBy={}",
                    reportId, report.getOrderTestId(), report.getSecondApprovedBy());
            throw new BadRequestAlertException(
                    "already_second_approved",
                    "diagnostic_order_tests_report",
                    "Report already second approved"
            );
        }

        String currentUser = currentUsername();

        if (currentUser.equals(report.getApprovedBy())) {
            LOG.warn("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - same user attempted second approval. reportId={} user={}",
                    reportId, currentUser);
            throw new BadRequestAlertException(
                    "same_user_not_allowed",
                    "diagnostic_order_tests_report",
                    "Second approve must be performed by a different user"
            );
        }

        report.setSecondApprovedBy(currentUser);
        report.setSecondApprovedDate(Instant.now());

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestReportService] SECOND_APPROVE_RADIOLOGY_REPORT - done. reportId={} orderTestId={} secondApprovedBy={}",
                saved.getId(), saved.getOrderTestId(), saved.getSecondApprovedBy());
        return saved;
    }

    private RadiologyImageStatusResponseVM setImageStatusByReport(Long orderTestId, RadiologyImageStatus imageStatusTo) {
        LOG.debug("[DiagnosticOrderTestReportService] SET_IMAGE_STATUS - start. orderTestId={} imageStatusTo={}", orderTestId, imageStatusTo);
        DiagnosticOrderTestReport report = diagnosticOrderTestReportRepository.findByOrderTestId(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + orderTestId
                ));

        ensureImageTransitionAllowed(report.getImageStatus(), imageStatusTo);

        report.setImageStatus(imageStatusTo);

        if (imageStatusTo == RadiologyImageStatus.FINISHED) {
            report.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        }

        DiagnosticOrderTestReport saved = diagnosticOrderTestReportRepository.save(report);

        recomputeOrderStatusesByOrderTestId(saved.getOrderTestId());

        RadiologyImageStatusResponseVM response = new RadiologyImageStatusResponseVM(
                saved.getId(),
                saved.getOrderTestId(),
                saved.getImageStatus(),
                saved.getLastModifiedDate()
        );

        LOG.debug("[DiagnosticOrderTestReportService] SET_IMAGE_STATUS - done. reportId={} orderTestId={} imageStatusTo={}",
                saved.getId(), saved.getOrderTestId(), saved.getImageStatus());
        return response;
    }

    private void ensureImageTransitionAllowed(RadiologyImageStatus imageStatusFrom, RadiologyImageStatus imageStatusTo) {
        LOG.debug("[DiagnosticOrderTestReportService] ENSURE_IMAGE_TRANSITION_ALLOWED - imageStatusFrom={} imageStatusTo={}", imageStatusFrom, imageStatusTo);
        if (imageStatusTo == RadiologyImageStatus.STARTED) {
            if (imageStatusFrom == null) return;
            if (imageStatusFrom == RadiologyImageStatus.STARTED || imageStatusFrom == RadiologyImageStatus.RESUMED || imageStatusFrom == RadiologyImageStatus.PAUSED)
                return;
            if (imageStatusFrom == RadiologyImageStatus.FINISHED)
                throw invalidImageTransition(imageStatusFrom, imageStatusTo);
            return;
        }

        if (imageStatusFrom == null) {
            LOG.warn("[DiagnosticOrderTestReportService] ENSURE_IMAGE_TRANSITION_ALLOWED - missing current image status. target={}", imageStatusTo);
            throw new BadRequestAlertException("missing_state", "diagnostic_order_tests_report", "Image status is missing");
        }

        if (imageStatusFrom == RadiologyImageStatus.FINISHED) {
            LOG.warn("[DiagnosticOrderTestReportService] ENSURE_IMAGE_TRANSITION_ALLOWED - transition blocked because workflow finished. from={} to={}",
                    imageStatusFrom, imageStatusTo);
            throw new BadRequestAlertException("invalid_transition", "diagnostic_order_tests_report", "Image workflow already finished");
        }

        boolean ok =
                (imageStatusFrom == RadiologyImageStatus.STARTED && (imageStatusTo == RadiologyImageStatus.PAUSED || imageStatusTo == RadiologyImageStatus.FINISHED)) ||
                        (imageStatusFrom == RadiologyImageStatus.PAUSED && imageStatusTo == RadiologyImageStatus.RESUMED) ||
                        (imageStatusFrom == RadiologyImageStatus.RESUMED && (imageStatusTo == RadiologyImageStatus.PAUSED || imageStatusTo == RadiologyImageStatus.FINISHED));

        if (!ok) throw invalidImageTransition(imageStatusFrom, imageStatusTo);
    }

    private BadRequestAlertException invalidImageTransition(RadiologyImageStatus from, RadiologyImageStatus to) {
        LOG.warn("[DiagnosticOrderTestReportService] ENSURE_IMAGE_TRANSITION_ALLOWED - invalid transition. from={} to={}", from, to);
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests_report",
                "Invalid transition " + from + " -> " + to
        );
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestReportResponseVM> filterReports(
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
            List<Long> patientIdIn,
            Long orderNumber,
            Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTestReportService] FILTER_REPORTS - start. id={} orderIdIn={} orderTestId={} severity={} approvedBy={} rejectedBy={} reviewBy={} reviewed={} patientIdIn={} orderNumber={} pageable={}",
                id, orderIdIn, orderTestId, severity, approvedBy, rejectedBy, reviewBy, reviewed, patientIdIn, orderNumber, pageable);

        Specification<DiagnosticOrderTestReport> spec = (reportRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (reviewed != null) {
                predicates.add(
                        reviewed
                                ? criteriaBuilder.isNotNull(reportRoot.get("reviewDate"))
                                : criteriaBuilder.isNull(reportRoot.get("reviewDate"))
                );
            }

            if (id != null) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("id"), id));
            }

            if (orderTestId != null) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("orderTestId"), orderTestId));
            }

            if (severity != null && !severity.isBlank()) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("severity"), severity));
            }

            if (approvedBy != null && !approvedBy.isBlank()) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("approvedBy"), approvedBy));
            }

            if (rejectedBy != null && !rejectedBy.isBlank()) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("rejectedBy"), rejectedBy));
            }

            if (reviewBy != null && !reviewBy.isBlank()) {
                predicates.add(criteriaBuilder.equal(reportRoot.get("reviewBy"), reviewBy));
            }

            if (approvedDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("approvedDate"), approvedDateFrom));
            }

            if (approvedDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("approvedDate"), approvedDateTo));
            }

            if (rejectedDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("rejectedDate"), rejectedDateFrom));
            }

            if (rejectedDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("rejectedDate"), rejectedDateTo));
            }

            if (reviewDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("reviewDate"), reviewDateFrom));
            }

            if (reviewDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("reviewDate"), reviewDateTo));
            }

            if (processingStatusIn != null && !processingStatusIn.isEmpty()) {
                predicates.add(reportRoot.get("processingStatus").in(processingStatusIn));
            }

            if (processingStatusNotIn != null && !processingStatusNotIn.isEmpty()) {
                predicates.add(criteriaBuilder.not(reportRoot.get("processingStatus").in(processingStatusNotIn)));
            }

            if (imageStatusIn != null && !imageStatusIn.isEmpty()) {
                predicates.add(reportRoot.get("imageStatus").in(imageStatusIn));
            }

            if (imageStatusNotIn != null && !imageStatusNotIn.isEmpty()) {
                predicates.add(criteriaBuilder.not(reportRoot.get("imageStatus").in(imageStatusNotIn)));
            }

            if (createdDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("createdDate"), createdDateFrom));
            }

            if (createdDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("createdDate"), createdDateTo));
            }

            if (lastModifiedDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(reportRoot.get("lastModifiedDate"), lastModifiedDateFrom));
            }

            if (lastModifiedDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(reportRoot.get("lastModifiedDate"), lastModifiedDateTo));
            }

            boolean needOrderFilter =
                    fromDepartmentIn != null && !fromDepartmentIn.isEmpty();

            boolean needOrderNumberFilter =
                    orderNumber != null;

            boolean needPatientIdFilter =
                    patientIdIn != null && !patientIdIn.isEmpty();

            boolean needPatientFilter =
                    (patientName != null && !patientName.isBlank()) ||
                            (mrn != null && !mrn.isBlank());

            boolean needOrderIdFilter =
                    orderIdIn != null && !orderIdIn.isEmpty();

            boolean needSubquery =
                    needOrderFilter ||
                            needOrderNumberFilter ||
                            needPatientIdFilter ||
                            needPatientFilter ||
                            needOrderIdFilter;

            if (needSubquery) {
                var subquery = criteriaQuery.subquery(Long.class);

                var testRoot = subquery.from(DiagnosticOrderTest.class);
                var orderRoot = subquery.from(DiagnosticOrder.class);

                List<Predicate> subPredicates = new ArrayList<>();

                subPredicates.add(criteriaBuilder.equal(
                        testRoot.get("id"),
                        reportRoot.get("orderTestId")
                ));

                subPredicates.add(criteriaBuilder.equal(
                        orderRoot.get("id"),
                        testRoot.get("orderId")
                ));

                if (needOrderIdFilter) {
                    subPredicates.add(orderRoot.get("id").in(orderIdIn));
                }

                if (needOrderFilter) {
                    subPredicates.add(orderRoot.get("fromDepartmentId").in(fromDepartmentIn));
                }

                if (needOrderNumberFilter) {
                    subPredicates.add(criteriaBuilder.equal(
                            orderRoot.get("orderNumber"),
                            orderNumber
                    ));
                }

                if (needPatientIdFilter) {
                    subPredicates.add(orderRoot.get("patientId").in(patientIdIn));
                }

                if (needPatientFilter) {
                    var patientRoot = subquery.from(Patient.class);

                    subPredicates.add(criteriaBuilder.equal(
                            patientRoot.get("id"),
                            orderRoot.get("patientId")
                    ));

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

                subquery.select(testRoot.get("id"))
                        .where(subPredicates.toArray(new Predicate[0]));

                predicates.add(criteriaBuilder.exists(subquery));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrderTestReport> page =
                diagnosticOrderTestReportRepository.findAll(spec, pageable);

        List<Long> reportIds = page.getContent()
                .stream()
                .map(DiagnosticOrderTestReport::getId)
                .toList();

        Set<Long> reportIdsWithNotes = reportIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(diagnosticOrderTestReportCommentsRepository
                .findDistinctReportIdByReportIdIn(reportIds));

        return page.map(report -> {
            boolean hasNote = reportIdsWithNotes.contains(report.getId());
            return DiagnosticOrderTestReportResponseVM.ofEntityWithNote(report, hasNote);
        });
    }
}
