package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportImageStatusLogRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestReportService;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportRejectDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportReviewDTO;
import com.dazzle.asklepios.service.dto.radiology.DiagnosticOrderTestReportUpdateDTO;
import com.dazzle.asklepios.service.dto.radiology.PacsStudyDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportImageStatusLogResponseVM;
import com.dazzle.asklepios.web.rest.vm.radiology.DiagnosticOrderTestReportResponseVM;
import com.dazzle.asklepios.web.rest.vm.radiology.RadiologyImageStatusResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing radiology reports ({@link DiagnosticOrderTestReport}).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Expose CRUD endpoints for reports.</li>
 *   <li>Expose controlled endpoints for review/reject/approve and image workflow.</li>
 *   <li>Expose filtering endpoint for reports.</li>
 * </ul>
 *
 * <p>Note: This controller does not access repositories directly; all persistence and
 * validation logic is delegated to {@link DiagnosticOrderTestReportService}.</p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestReportController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportController.class);

    private final DiagnosticOrderTestReportService reportService;
    private final DiagnosticOrderTestReportImageStatusLogRepository logRepository;

    public DiagnosticOrderTestReportController(DiagnosticOrderTestReportService reportService, DiagnosticOrderTestReportImageStatusLogRepository logRepository) {
        this.reportService = reportService;

        this.logRepository = logRepository;
    }

    /**
     * Filters radiology diagnostic order test reports using dynamic criteria.
     *
     * <p>All filtering is executed in the service layer and supports pagination.</p>
     */
    @GetMapping("/radiology/reports")
    public ResponseEntity<List<DiagnosticOrderTestReportResponseVM>> filterReports(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "orderIdIn", required = false) List<Long> orderIdIn,
            @RequestParam(name = "orderTestId", required = false) Long orderTestId,

            @RequestParam(name = "severity", required = false) Severity severity,

            @RequestParam(name = "approvedBy", required = false) String approvedBy,
            @RequestParam(name = "rejectedBy", required = false) String rejectedBy,
            @RequestParam(name = "reviewBy", required = false) String reviewBy,
            @RequestParam(name = "reviewed", required = false) Boolean reviewed,

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
            @RequestParam(name = "patientIdIn", required = false) List<Long> patientIdIn,
            @RequestParam(name = "orderNumber", required = false) Long orderNumber,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[RadiologyReport] FILTER orderIdIn={} orderTestId={} fromDepartmentIn={} patientName={} mrn={} patientIdIn={} orderNumber={}",
                orderIdIn, orderTestId, fromDepartmentIn, patientName, mrn, patientIdIn, orderNumber);

        Page<DiagnosticOrderTestReportResponseVM> page = reportService.filterReports(
                id,
                orderIdIn,
                orderTestId,
                severity,
                approvedBy,
                rejectedBy,
                reviewBy,
                reviewed,
                approvedDateFrom,
                approvedDateTo,
                rejectedDateFrom,
                rejectedDateTo,
                reviewDateFrom,
                reviewDateTo,
                processingStatusIn,
                processingStatusNotIn,
                imageStatusIn,
                imageStatusNotIn,
                createdDateFrom,
                createdDateTo,
                lastModifiedDateFrom,
                lastModifiedDateTo,
                fromDepartmentIn,
                patientName,
                mrn,
                patientIdIn,
                orderNumber,
                pageable
        );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/radiology/reports/by-test/{orderTestId}")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> getByOrderTestId(
            @PathVariable Long orderTestId
    ) {
        LOG.debug("[RadiologyReport] GET_BY_TEST orderTestId={}", orderTestId);

        Optional<DiagnosticOrderTestReport> reportOpt =
                reportService.findByOrderTestIdForRadiology(orderTestId);

        if (reportOpt.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204
        }

        return ResponseEntity.ok(
                DiagnosticOrderTestReportResponseVM.ofEntity(reportOpt.get())
        );
    }

    @PostMapping("/radiology/reports")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> create(@Valid @RequestBody DiagnosticOrderTestReportCreateDTO orderTestReportCreateDTO) {
        LOG.debug("[RadiologyReport] CREATE payload={}", orderTestReportCreateDTO);

        DiagnosticOrderTestReport saved = reportService.createRadiologyReport(orderTestReportCreateDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/radiology/reports/" + saved.getId()))
                .body(DiagnosticOrderTestReportResponseVM.ofEntity(saved));
    }

    @PutMapping("/radiology/reports/{reportId}")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> update(
            @PathVariable Long reportId,
            @Valid @RequestBody DiagnosticOrderTestReportUpdateDTO orderTestReportUpdateDTO
    ) {
        LOG.debug("[RadiologyReport] UPDATE reportId={} payload={}", reportId, orderTestReportUpdateDTO);


        DiagnosticOrderTestReport updated = reportService.updateRadiologyReport(reportId, orderTestReportUpdateDTO);

        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/review/toggle")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> toggleReview(
            @Valid @RequestBody DiagnosticOrderTestReportReviewDTO dto) {

        LOG.debug("[RadiologyReport] TOGGLE REVIEW payload={}", dto);

        DiagnosticOrderTestReport updated =
                reportService.reviewRadiologyReport(dto);

        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/reject")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> reject(@Valid @RequestBody DiagnosticOrderTestReportRejectDTO orderTestReportRejectDTO) {
        LOG.debug("[RadiologyReport] REJECT payload={}", orderTestReportRejectDTO);

        DiagnosticOrderTestReport updated = reportService.rejectRadiologyReport(orderTestReportRejectDTO);

        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/image/{testId}/start")
    public ResponseEntity<RadiologyImageStatusResponseVM> startImage(@PathVariable Long testId) {
        LOG.debug("[RadiologyReport] IMAGE_START testId={}", testId);

        return ResponseEntity.ok(reportService.startRadiologyImage(testId));
    }

    @PostMapping("/radiology/reports/image/{testId}/pause")
    public ResponseEntity<RadiologyImageStatusResponseVM> pauseImage(@PathVariable Long testId) {
        LOG.debug("[RadiologyReport] IMAGE_PAUSE testId={}", testId);

        return ResponseEntity.ok(reportService.pauseRadiologyImage(testId));
    }

    @PostMapping("/radiology/reports/image/{testId}/resume")
    public ResponseEntity<RadiologyImageStatusResponseVM> resumeImage(@PathVariable Long testId) {
        LOG.debug("[RadiologyReport] IMAGE_RESUME testId={}", testId);

        return ResponseEntity.ok(reportService.resumeRadiologyImage(testId));
    }

    @PostMapping("/radiology/reports/image/{testId}/finish")
    public ResponseEntity<RadiologyImageStatusResponseVM> finishImage(@PathVariable Long testId) {
        LOG.debug("[RadiologyReport] IMAGE_FINISH testId={}", testId);

        return ResponseEntity.ok(reportService.finishRadiologyImage(testId));
    }

    @PostMapping("/radiology/reports/{reportId}/approve")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> approve(@PathVariable Long reportId) {
        LOG.debug("[RadiologyReport] APPROVE reportId={}", reportId);

        DiagnosticOrderTestReport updated = reportService.approveRadiologyReport(reportId);

        return ResponseEntity.ok(DiagnosticOrderTestReportResponseVM.ofEntity(updated));
    }

    @PostMapping("/radiology/reports/{reportId}/second-approve")
    public ResponseEntity<DiagnosticOrderTestReportResponseVM> secondApprove(@PathVariable Long reportId) {
        LOG.debug("[RadiologyReport] SECOND_APPROVE reportId={}", reportId);

        DiagnosticOrderTestReport updated = reportService.secondApproveRadiologyReport(reportId);

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

    @GetMapping("/radiology/reports/{id}/image-links")
    public ResponseEntity<List<PacsStudyDTO>> getImageLinks(
            @PathVariable Long id
    ) {
        LOG.debug("REST request to get image links for report : {}", id);

        List<PacsStudyDTO> imageLinks = reportService.getImageLinks(id);

        return ResponseEntity.ok(imageLinks);
    }

}