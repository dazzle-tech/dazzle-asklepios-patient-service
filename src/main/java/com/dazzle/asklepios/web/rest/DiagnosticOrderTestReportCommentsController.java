package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import com.dazzle.asklepios.service.DiagnosticOrderTestReportCommentsService;
import com.dazzle.asklepios.service.dto.radiology.comments.DiagnosticOrderTestReportCommentsDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/api/patient/report-comments")
public class DiagnosticOrderTestReportCommentsController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticOrderTestReportCommentsController.class);

    private final DiagnosticOrderTestReportCommentsService service;

    public DiagnosticOrderTestReportCommentsController(DiagnosticOrderTestReportCommentsService service) {
        this.service = service;
    }

    /**
     * Create a new report comment.
     *
     * @param dto request payload
     * @return created entity
     */
    @PostMapping
    public ResponseEntity<DiagnosticOrderTestReportComments> create(
            @Valid @RequestBody DiagnosticOrderTestReportCommentsDTO dto
    ) {
        log.debug("REST request to create DiagnosticOrderTestReportComments dto={}", dto);

        DiagnosticOrderTestReportComments created = service.create(dto);

        log.info("REST created DiagnosticOrderTestReportComments id={} reportId={} orderTestId={}",
                created.getId(), created.getReportId(), created.getOrderTestId());

        return ResponseEntity.ok(created);
    }

    /**
     * Get all report comments by report id.
     *
     * @param reportId report id
     * @return list of comments for the given report
     */
    @GetMapping("/by-report/{reportId}")
    public ResponseEntity<List<DiagnosticOrderTestReportComments>> getByReportId(@PathVariable Long reportId) {
        Objects.requireNonNull(reportId, "reportId must not be null");
        log.debug("REST request to get DiagnosticOrderTestReportComments by reportId={}", reportId);

        List<DiagnosticOrderTestReportComments> result = service.getByReportId(reportId);

        log.info("REST response: {} DiagnosticOrderTestReportComments for reportId={}", result.size(), reportId);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a report comment by id.
     *
     * @param id comment id
     * @return 204 No Content if deleted
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Objects.requireNonNull(id, "id must not be null");
        log.debug("REST request to delete DiagnosticOrderTestReportComments id={}", id);

        service.delete(id);

        log.info("REST deleted DiagnosticOrderTestReportComments id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Maps {@link EntityNotFoundException} to 404 response.
     *
     * @param ex exception thrown when entity is not found
     * @return 404 Not Found
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(EntityNotFoundException ex) {
        log.warn("Report comment not found: {}", ex.getMessage());
        return ResponseEntity.notFound().build();
    }
}
