package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportCommentsRepository;
import com.dazzle.asklepios.service.dto.radiology.comments.DiagnosticOrderTestReportCommentsDTO;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DiagnosticOrderTestReportCommentsService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticOrderTestReportCommentsService.class);

    private final DiagnosticOrderTestReportCommentsRepository noteRepository;

    public DiagnosticOrderTestReportCommentsService(DiagnosticOrderTestReportCommentsRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /**
     * Create a new report comment.
     *
     * @param dto request payload
     * @return persisted entity
     */
    public DiagnosticOrderTestReportComments create(DiagnosticOrderTestReportCommentsDTO dto) {
        Objects.requireNonNull(dto, "dto must not be null");
        log.debug("Request to create DiagnosticOrderTestReportComments dto={}", dto);

        DiagnosticOrderTestReportComments n = new DiagnosticOrderTestReportComments();
        n.setOrderTestId(dto.orderTestId());
        n.setReportId(dto.reportId());
        n.setNote(dto.note());

        DiagnosticOrderTestReportComments saved = noteRepository.save(n);
        log.info("Created DiagnosticOrderTestReportComments id={} reportId={} orderTestId={}",
                saved.getId(), saved.getReportId(), saved.getOrderTestId());

        return saved;
    }

    /**
     * Delete a report comment by id.
     *
     * @param id comment id
     */
    public void delete(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        log.debug("Request to delete DiagnosticOrderTestReportComments id={}", id);

        if (!noteRepository.existsById(id)) {
            log.warn("DiagnosticOrderTestReportComments not found for delete id={}", id);
            throw new EntityNotFoundException("DiagnosticOrderTestReportComments not found with id=" + id);
        }

        noteRepository.deleteById(id);
        log.info("Deleted DiagnosticOrderTestReportComments id={}", id);
    }

    /**
     * Get all report comments by reportId.
     *
     * @param reportId report id
     * @return list of comments (empty if none)
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestReportComments> getByReportId(Long reportId) {
        Objects.requireNonNull(reportId, "reportId must not be null");
        log.debug("Request to get DiagnosticOrderTestReportComments by reportId={}", reportId);

        // لازم يكون موجود بالـ Repository:
        // List<DiagnosticOrderTestReportComments> findByReportId(Long reportId);
        List<DiagnosticOrderTestReportComments> result = noteRepository.findByReportId(reportId);

        log.info("Found {} DiagnosticOrderTestReportComments for reportId={}", result.size(), reportId);
        return result;
    }
}
