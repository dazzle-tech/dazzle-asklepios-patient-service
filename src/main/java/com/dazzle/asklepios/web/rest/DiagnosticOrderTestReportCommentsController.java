package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import com.dazzle.asklepios.service.DiagnosticOrderTestReportCommentsService;
import com.dazzle.asklepios.service.dto.radiology.comments.DiagnosticOrderTestReportCommentsDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST controller for managing {@link DiagnosticOrderTestReportComments}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/radiology/report-comments : create comment</li>
 *   <li>GET  /api/radiology/report-comments/by-report/{reportId} : list comments by report</li>
 *   <li>DELETE /api/radiology/report-comments/{id} : delete comment</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestReportCommentsController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportCommentsController.class);

    private final DiagnosticOrderTestReportCommentsService service;

    public DiagnosticOrderTestReportCommentsController(DiagnosticOrderTestReportCommentsService service) {
        this.service = service;
    }

    /**
     * Create a new report comment.
     *
     * @param orderTestReportCommentsDTO request payload
     * @return created entity
     */
    @PostMapping("/report-comments")
    public ResponseEntity<DiagnosticOrderTestReportComments> create(
            @Valid @RequestBody DiagnosticOrderTestReportCommentsDTO orderTestReportCommentsDTO
    ) {
        LOG.debug("REST request to create DiagnosticOrderTestReportComments orderTestReportCommentsDTO={}", orderTestReportCommentsDTO);

        DiagnosticOrderTestReportComments created = service.create(orderTestReportCommentsDTO);

        LOG.info("REST created DiagnosticOrderTestReportComments id={} reportId={} orderTestId={}",
                created.getId(), created.getReportId(), created.getOrderTestId());

        return ResponseEntity.ok(created);
    }

    /**
     * Get all report comments by report id.
     *
     * @param reportId report id
     * @return list of comments for the given report
     */
    @GetMapping("/report-comments/by-report/{reportId}")
    public ResponseEntity<List<DiagnosticOrderTestReportComments>> getByReportId(@PathVariable Long reportId) {
        Objects.requireNonNull(reportId, "reportId must not be null");
        LOG.debug("REST request to get DiagnosticOrderTestReportComments by reportId={}", reportId);

        List<DiagnosticOrderTestReportComments> result = service.getByReportId(reportId);

        LOG.info("REST response: {} DiagnosticOrderTestReportComments for reportId={}", result.size(), reportId);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a report comment by id.
     *
     * @param id comment id
     * @return 204 No Content if deleted
     */
    @DeleteMapping("/report-comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Objects.requireNonNull(id, "id must not be null");
        LOG.debug("REST request to delete DiagnosticOrderTestReportComments id={}", id);

        service.delete(id);

        LOG.info("REST deleted DiagnosticOrderTestReportComments id={}", id);
        return ResponseEntity.noContent().build();
    }


}
