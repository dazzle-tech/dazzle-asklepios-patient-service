package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportApproveDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service layer for managing {@link DiagnosticOrderTestReport}.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Persist report records (create/update).</li>
 *   <li>Apply controlled report state changes (review/reject) using system user/time.</li>
 *   <li>Apply image workflow transitions (start/pause/resume/finish) and enforce allowed transitions.</li>
 *   <li>Recompute aggregated order statuses after each write.</li>
 * </ul>
 * <p>
 * Controller handles validation related to test existence/type and prerequisites like ACCEPTED.
 */
@Service
@Transactional
public class DiagnosticOrderTestReportService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportService.class);

    private final DiagnosticOrderTestReportRepository reportRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    public DiagnosticOrderTestReportService(
            DiagnosticOrderTestReportRepository reportRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService, DiagnosticOrderTestStatusService diagnosticOrderTestStatusService


            ) {
        this.reportRepository = reportRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
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
     * Loads a report by orderTestId (DiagnosticOrderTest id).
     *
     * @param orderTestId DiagnosticOrderTest id
     * @return existing report
     */
    @Transactional(readOnly = true)
    public DiagnosticOrderTestReport getByOrderTestId(Long orderTestId) {
        LOG.debug("Service get report by orderTestId={}", orderTestId);
        return reportRepository.findByOrderTestId(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + orderTestId
                ));
    }

    /**
     * Creates and persists a new report record.
     * <p>
     * Report processingStatus starts at NEW by default.
     * Caller enforces duplicate constraints and test prerequisites.
     *
     * @param dto create payload
     * @return saved report
     */
    public DiagnosticOrderTestReport create(DiagnosticOrderTestReportCreateDTO dto) {
        LOG.debug("Service create report orderId={} orderTestId={}", dto.orderId(), dto.orderTestId());

        DiagnosticOrderTestReport report = DiagnosticOrderTestReport.builder()
                .orderId(dto.orderId())
                .orderTestId(dto.orderTestId())
                .report(dto.report())
                .severity(dto.severity())
                .processingStatus(DiagnosticStatus.NEW)
                .imageStatus(dto.imageStatus())
                .approvedBy(dto.approvedBy())
                .approvedDate(dto.approvedDate())
                .rejectedBy(dto.rejectedBy())
                .rejectedDate(dto.rejectedDate())
                .rejectedReason(dto.rejectedReason())
                .reviewBy(dto.reviewBy())
                .reviewDate(dto.reviewDate())
                .build();

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Updates report core fields.
     *
     * @param reportId report id
     * @param dto update payload
     * @return updated report
     */
    public DiagnosticOrderTestReport update(Long reportId, DiagnosticOrderTestReportUpdateDTO dto) {
        LOG.debug("Service update report id={}", reportId);

        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        report.setReport(dto.report());
        report.setSeverity(dto.severity());
        report.setProcessingStatus(dto.processingStatus());
        report.setImageStatus(dto.imageStatus());

        report.setApprovedBy(dto.approvedBy());
        report.setApprovedDate(dto.approvedDate());
        report.setRejectedBy(dto.rejectedBy());
        report.setRejectedDate(dto.rejectedDate());
        report.setRejectedReason(dto.rejectedReason());
        report.setReviewBy(dto.reviewBy());
        report.setReviewDate(dto.reviewDate());

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Marks a report as REJECTED using system user/time.
     *
     * @param dto reject payload
     * @return updated report
     */
    public DiagnosticOrderTestReport reject(DiagnosticOrderTestReportRejectDTO dto) {
        LOG.debug("Service reject report orderTestId={}", dto.orderTestId());

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        String user = currentUsername();

        report.setRejectedBy(user);
        report.setRejectedReason(dto.rejectedReason());
        report.setRejectedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REJECTED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Marks a report as REVIEWED using system user/time.
     *
     * @param dto review payload
     * @return updated report
     */
    public DiagnosticOrderTestReport review(DiagnosticOrderTestReportReviewDTO dto) {
        LOG.debug("Service review report orderTestId={}", dto.orderTestId());

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        String user =currentUsername();

        report.setReviewBy(user);
        report.setReviewDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Starts imaging by ensuring a report exists and setting imageStatus=STARTED.
     *
     * @param orderId parent order id
     * @param orderTestId DiagnosticOrderTest id
     * @return image workflow VM
     */
    public RadiologyImageStatusResponseVM startImage(Long orderId, Long orderTestId) {
        LOG.debug("Service start image orderId={} orderTestId={}", orderId, orderTestId);

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(orderTestId)
                .orElseGet(() -> DiagnosticOrderTestReport.builder()
                        .orderId(orderId)
                        .orderTestId(orderTestId)
                        .processingStatus(DiagnosticStatus.NEW)
                        .build());

        ensureImageTransitionAllowed(report.getImageStatus(), RadiologyImageStatus.STARTED);

        report.setImageStatus(RadiologyImageStatus.STARTED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return new RadiologyImageStatusResponseVM(
                saved.getId(),
                saved.getOrderId(),
                saved.getOrderTestId(),
                saved.getImageStatus(),
                saved.getLastModifiedDate()
        );
    }

    public RadiologyImageStatusResponseVM pauseImage(Long testId) {
        return setImageStatusByReport(testId, RadiologyImageStatus.PAUSED);
    }

    public RadiologyImageStatusResponseVM resumeImage(Long testId) {
        return setImageStatusByReport(testId, RadiologyImageStatus.RESUMED);
    }

    /**
     * Finishes imaging for a report and sets report.processingStatus=RESULT_READY.
     *
     * @param testId DiagnosticOrderTest id
     * @return image workflow VM
     */
    public RadiologyImageStatusResponseVM finishImage(Long testId) {
        return setImageStatusByReport(testId, RadiologyImageStatus.FINISHED);
    }

    public DiagnosticOrderTestReport approve(Long reportId) {
        LOG.debug("Service approve reportId={}", reportId);

        DiagnosticOrderTestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found with id " + reportId
                ));

        Long orderTestId = report.getOrderTestId();

        String user = currentUsername();
        Instant now = Instant.now();

        report.setApprovedBy(user);
        report.setApprovedDate(now);
        report.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);

        DiagnosticOrderTestReport saved = reportRepository.save(report);


        diagnosticOrderTestStatusService.approve(orderTestId);

        return saved;
    }

    private RadiologyImageStatusResponseVM setImageStatusByReport(Long testId, RadiologyImageStatus to) {
        LOG.debug("Service set imageStatus orderTestId={} to={}", testId, to);

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
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return new RadiologyImageStatusResponseVM(
                saved.getId(),
                saved.getOrderId(),
                saved.getOrderTestId(),
                saved.getImageStatus(),
                saved.getLastModifiedDate()
        );
    }
    public DiagnosticOrderTestReport secondApprove(DiagnosticOrderTestReport report) {
        LOG.debug("Service second approve reportId={}", report.getId());

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

        Instant now = Instant.now();

        report.setSecondApprovedBy(currentUser);
        report.setSecondApprovedDate(now);

        DiagnosticOrderTestReport saved = reportRepository.save(report);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }


    /**
     * Validates allowed radiology image workflow transitions.
     *
     * @param from current status (may be null if never started)
     * @param to target status
     */
    private void ensureImageTransitionAllowed(RadiologyImageStatus from, RadiologyImageStatus to) {
        if (to == RadiologyImageStatus.STARTED) {
            if (from == null) return;
            if (from == RadiologyImageStatus.STARTED || from == RadiologyImageStatus.RESUMED || from == RadiologyImageStatus.PAUSED) return;
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
                        (from == RadiologyImageStatus.PAUSED && (to == RadiologyImageStatus.RESUMED)) ||
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
}
