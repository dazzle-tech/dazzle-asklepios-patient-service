package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for create/update/delete operations for {@link DiagnosticOrderTestResult}.
 *
 * <p>This service handles result data persistence and enforces basic invariants:
 * <ul>
 *   <li>New results are created with {@link DiagnosticStatus#RESULT_READY} as processing status.</li>
 *   <li>Status transitions (approve/reject/review) are not handled here; use
 *       {@link DiagnosticOrderTestResultStatusService} instead.</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class DiagnosticOrderTestResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestResultService.class);

    /** Repository for persistence operations. */
    private final DiagnosticOrderTestResultRepository repository;

    /** Service that recomputes parent test/order state from results. */
    private final DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService;

    /**
     * Constructs the service with required dependencies.
     *
     * @param repository repository for result persistence
     * @param diagnosticOrderTestResultStatusService service for recompute/aggregate from results
     */
    public DiagnosticOrderTestResultService(
            DiagnosticOrderTestResultRepository repository,
            DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService
    ) {
        this.repository = repository;
        this.diagnosticOrderTestResultStatusService = diagnosticOrderTestResultStatusService;
    }

    /**
     * Creates a new result.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Builds a new {@link DiagnosticOrderTestResult} from the DTO.</li>
     *   <li>Sets {@code processingStatus} to {@link DiagnosticStatus#RESULT_READY}.</li>
     *   <li>Saves the result.</li>
     *   <li>Recomputes the parent test processing status from all its results.</li>
     * </ul>
     * </p>
     *
     * @param dto create payload
     * @return persisted result
     */
    public DiagnosticOrderTestResult create(DiagnosticOrderTestResultCreateDTO dto) {
        LOG.debug("[DiagnosticOrderTestResultService] CREATE - start. payload={}", dto);

        DiagnosticOrderTestResult r = new DiagnosticOrderTestResult();
        r.setOrderId(dto.orderId());
        r.setOrderTestId(dto.orderTestId());
        r.setProfileTestId(dto.profileTestId());

        r.setResultValueNumber(dto.resultValueNumber());
        r.setResultValueText(dto.resultValueText());
        r.setMarker(dto.marker());
        r.setNormalRangeValue(dto.normalRangeValue());

        r.setProcessingStatus(DiagnosticStatus.RESULT_READY);

        DiagnosticOrderTestResult saved = repository.save(r);

        diagnosticOrderTestResultStatusService.recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestResultService] CREATE - done. id={} orderTestId={} processingStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProcessingStatus());

        return saved;
    }

    /**
     * Updates result data fields only.
     *
     * <p>This method does not modify status/audit fields for approval/rejection/review.
     * Use {@link DiagnosticOrderTestResultStatusService} for those operations.</p>
     *
     * @param existing current persisted entity
     * @param dto update payload
     * @return updated persisted entity
     */
    public DiagnosticOrderTestResult update(DiagnosticOrderTestResult existing, DiagnosticOrderTestResultUpdateDTO dto) {
        LOG.debug("[DiagnosticOrderTestResultService] UPDATE - start. id={} payload={}", existing.getId(), dto);

        existing.setOrderId(dto.orderId());
        existing.setOrderTestId(dto.orderTestId());
        existing.setProfileTestId(dto.profileTestId());

        existing.setResultValueNumber(dto.resultValueNumber());
        existing.setResultValueText(dto.resultValueText());
        existing.setMarker(dto.marker());
        existing.setNormalRangeValue(dto.normalRangeValue());

        DiagnosticOrderTestResult saved = repository.save(existing);

        LOG.debug("[DiagnosticOrderTestResultService] UPDATE - done. id={} processingStatus={}",
                saved.getId(), saved.getProcessingStatus());

        return saved;
    }

    /**
     * Deletes a result by id.
     *
     * @param id result id
     */
    public void delete(Long id) {
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - start. id={}", id);
        repository.deleteById(id);
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - done. id={}", id);
    }
}
