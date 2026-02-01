// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestReportService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
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
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DiagnosticOrderTestReportService {

    private final DiagnosticOrderTestReportRepository reportRepository;
    private final DiagnosticOrderTestRepository testRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    public DiagnosticOrderTestReportService(
            DiagnosticOrderTestReportRepository reportRepository,
            DiagnosticOrderTestRepository testRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.reportRepository = reportRepository;
        this.testRepository = testRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    public DiagnosticOrderTestReport create(DiagnosticOrderTestReportCreateDTO dto) {
        reportRepository.findByOrderIdAndOrderTestId(dto.orderId(), dto.orderTestId())
                .ifPresent(r -> {
                    throw new BadRequestAlertException(
                            "already_exists",
                            "diagnostic_order_tests_report",
                            "Report already exists for orderId=" + dto.orderId() + " orderTestId=" + dto.orderTestId()
                    );
                });

        DiagnosticOrderTestReport report = DiagnosticOrderTestReport.builder()
                .orderId(dto.orderId())
                .orderTestId(dto.orderTestId())
                .report(dto.report())
                .severity(dto.severity())
                .processingStatus(dto.processingStatus())
                .imageStatus(dto.imageStatus())
                .approvedBy(dto.approvedBy())
                .approvedDate(dto.approvedDate())
                .rejectedBy(dto.rejectedBy())
                .rejectedDate(dto.rejectedDate())
                .rejectedReason(dto.rejectedReason())
                .reviewBy(dto.reviewBy())
                .reviewDate(dto.reviewDate())
                .build();

        return reportRepository.save(report);
    }

    public DiagnosticOrderTestReport update(Long reportId, DiagnosticOrderTestReportUpdateDTO dto) {
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

        return reportRepository.save(report);
    }

    public DiagnosticOrderTestReport reject(DiagnosticOrderTestReportRejectDTO dto) {
        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        String user = SecurityUtils.getCurrentUserLogin().orElse("system");

        report.setRejectedBy(user);
        report.setRejectedReason(dto.rejectedReason());
        report.setRejectedDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REJECTED);

        return reportRepository.save(report);
    }

    public DiagnosticOrderTestReport review(DiagnosticOrderTestReportReviewDTO dto) {
        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_report",
                        "Report not found for orderTestId " + dto.orderTestId()
                ));

        String user = SecurityUtils.getCurrentUserLogin().orElse("system");

        report.setReviewBy(user);
        report.setReviewDate(Instant.now());
        report.setProcessingStatus(DiagnosticStatus.REVIEWED);

        return reportRepository.save(report);
    }

    public RadiologyImageStatusResponseVM pauseImage(Long testId) {
        return setImageStatus(testId, RadiologyImageStatus.PAUSED);
    }

    public RadiologyImageStatusResponseVM resumeImage(Long testId) {
        return setImageStatus(testId, RadiologyImageStatus.RESUMED);
    }

    public RadiologyImageStatusResponseVM finishImage(Long testId) {
        return setImageStatus(testId, RadiologyImageStatus.FINISHED);
    }

    private RadiologyImageStatusResponseVM setImageStatus(Long testId, RadiologyImageStatus to) {
        DiagnosticOrderTest test = testRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + testId
                ));

        if (test.getOrderType() != TestType.RADIOLOGY) {
            throw new BadRequestAlertException("not_radiology", "diagnostic_order_tests", "Test is not radiology");
        }

        DiagnosticOrderTestReport report = reportRepository.findByOrderTestId(testId)
                .orElseGet(() -> DiagnosticOrderTestReport.builder()
                        .orderId(test.getOrderId())
                        .orderTestId(test.getId())
                        .build());

        RadiologyImageStatus from = report.getImageStatus();

        if (from == RadiologyImageStatus.FINISHED && to != RadiologyImageStatus.FINISHED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_order_tests_report",
                    "Invalid transition " + from + " -> " + to
            );
        }

        report.setImageStatus(to);

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
}
