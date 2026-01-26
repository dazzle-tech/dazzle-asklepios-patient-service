// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestResultService.java
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

@Service
@Transactional
public class DiagnosticOrderTestResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestResultService.class);

    private final DiagnosticOrderTestResultRepository repository;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService;


    public DiagnosticOrderTestResultService(
            DiagnosticOrderTestResultRepository repository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService, DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService
    ) {
        this.repository = repository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestResultStatusService = diagnosticOrderTestResultStatusService;
    }

    /**
     * Create result:
     * - processingStatus is controlled here (RESULT_READY).
     * - triggers test transition to RESULT_READY via DiagnosticOrderTestStatusService.markReady(testId).
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


        diagnosticOrderTestResultStatusService.recomputeTestProcessingStatusFromResults(r.getOrderTestId());
        LOG.debug("[DiagnosticOrderTestResultService] CREATE - done. id={} orderTestId={} processingStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProcessingStatus());

        return saved;
    }

    /**
     * Update result data only:.
     * - does NOT allow changing processingStatus / approve / reject fields here.
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

    public void delete(Long id) {
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - start. id={}", id);
        repository.deleteById(id);
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - done. id={}", id);
    }
}
