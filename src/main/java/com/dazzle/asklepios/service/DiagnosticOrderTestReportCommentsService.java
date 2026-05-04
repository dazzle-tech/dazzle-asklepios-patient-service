package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportCommentsRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.radiology.comments.DiagnosticOrderTestReportCommentsDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestReportCommentsService.class);

    private final DiagnosticOrderTestReportCommentsRepository noteRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;

    public DiagnosticOrderTestReportCommentsService(DiagnosticOrderTestReportCommentsRepository noteRepository, DiagnosticOrderTestRepository diagnosticOrderTestRepository, DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository) {
        this.noteRepository = noteRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderTestReportRepository = diagnosticOrderTestReportRepository;
    }

    /**
     * Create a new report comment.
     *
     * @param orderTestReportCommentsDTO request payload
     * @return persisted entity
     */
    public DiagnosticOrderTestReportComments create(DiagnosticOrderTestReportCommentsDTO orderTestReportCommentsDTO) {
        Objects.requireNonNull(orderTestReportCommentsDTO, "orderTestReportCommentsDTO must not be null");
        LOG.debug("Request to create DiagnosticOrderTestReportComments orderTestReportCommentsDTO={}", orderTestReportCommentsDTO);
        DiagnosticOrderTest diagnosticOrderTest = getDiagnosticOrderTest(orderTestReportCommentsDTO.orderTestId());
        DiagnosticOrderTestReport diagnosticOrderTestReport = getDiagnosticOrderTestReport(orderTestReportCommentsDTO.reportId());
        DiagnosticOrderTestReportComments orderTestReportComments = new DiagnosticOrderTestReportComments();
        orderTestReportComments.setOrderTestId(diagnosticOrderTest.getId());
        orderTestReportComments.setReportId(diagnosticOrderTestReport.getId());
        orderTestReportComments.setNote(orderTestReportCommentsDTO.note());

        DiagnosticOrderTestReportComments saved = noteRepository.save(orderTestReportComments);
        LOG.info("Created DiagnosticOrderTestReportComments id={} reportId={} orderTestId={}",
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
        LOG.debug("Request to delete DiagnosticOrderTestReportComments id={}", id);

        if (!noteRepository.existsById(id)) {
            LOG.warn("DiagnosticOrderTestReportComments not found for delete id={}", id);
            throw new EntityNotFoundException("DiagnosticOrderTestReportComments not found with id=" + id);
        }

        noteRepository.deleteById(id);
        LOG.info("Deleted DiagnosticOrderTestReportComments id={}", id);
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
        LOG.debug("Request to get DiagnosticOrderTestReportComments by reportId={}", reportId);


        List<DiagnosticOrderTestReportComments> result = noteRepository.findByReportId(reportId);

        LOG.info("Found {} DiagnosticOrderTestReportComments for reportId={}", result.size(), reportId);
        return result;
    }

    private DiagnosticOrderTestReport getDiagnosticOrderTestReport(Long diagnosticOrderTestReportId) {
        LOG.debug("[TechnicianNoteService]  getDiagnosticOrderTestReport:  id={}", diagnosticOrderTestReportId);

        return diagnosticOrderTestReportRepository.findById(diagnosticOrderTestReportId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnosticOrdersTestReport",
                        "Report not found with id " + diagnosticOrderTestReportId
                ));
    }

    private DiagnosticOrderTest getDiagnosticOrderTest(Long diagnosticOrderTestId) {
        LOG.debug("[TechnicianNoteService]  getDiagnosticOrderTest:  id={}", diagnosticOrderTestId);

        return diagnosticOrderTestRepository.findById(diagnosticOrderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnosticOrdersTest",
                        "Test not found with id " + diagnosticOrderTestId
                ));
    }
}
