package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import com.dazzle.asklepios.repository.DiagnosticOrderTestCollectedSampleRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkSameDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestCollectedSampleService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestCollectedSampleService.class);

    private final DiagnosticOrderTestCollectedSampleRepository repository;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    public DiagnosticOrderTestCollectedSampleService(
            DiagnosticOrderTestCollectedSampleRepository repository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService
    ) {
        this.repository = repository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
    }

    public DiagnosticOrderTestCollectedSample create(DiagnosticOrderTestCollectedSampleDTO dto) {
        LOG.debug("[CollectedSampleService] CREATE - start. payload={}", dto);

        // Build collected sample entity
        DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
        s.setOrderId(dto.orderId());
        s.setOrderTestId(dto.orderTestId());
        s.setUnit(dto.unit());
        s.setQuantity(dto.quantity());
        s.setCollectedAt(dto.collectedAt());

        // Persist collected sample
        DiagnosticOrderTestCollectedSample saved = repository.save(s);

        // Update test processing status: NEW -> SAMPLE_COLLECTED
        diagnosticOrderTestStatusService.collectSample(dto.orderTestId());

        LOG.debug("[CollectedSampleService] CREATE - done. id={} orderId={} orderTestId={}",
                saved.getId(), saved.getOrderId(), saved.getOrderTestId());
        return saved;
    }

    public List<DiagnosticOrderTestCollectedSample> bulkCreateWithSameDetails(
            DiagnosticOrderTestCollectedSampleBulkSameDTO dto
    ) {
        LOG.debug("[CollectedSampleService] BULK_CREATE_SAME - start. orderId={} orderTestIdsCount={}",
                dto.orderId(), dto.orderTestIds() == null ? 0 : dto.orderTestIds().size());

        // Build entities for all orderTestIds
        List<DiagnosticOrderTestCollectedSample> entities = dto.orderTestIds().stream().map(orderTestId -> {
            DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
            s.setOrderId(dto.orderId());
            s.setOrderTestId(orderTestId);
            s.setUnit(dto.unit());
            s.setQuantity(dto.quantity());
            s.setCollectedAt(dto.collectedAt());
            return s;
        }).toList();

        // Persist all collected samples
        List<DiagnosticOrderTestCollectedSample> saved = repository.saveAll(entities);

        // Update processing status for each test
        dto.orderTestIds().forEach(diagnosticOrderTestStatusService::collectSample);

        LOG.debug("[CollectedSampleService] BULK_CREATE_SAME - done. savedCount={} orderId={}",
                saved.size(), dto.orderId());
        return saved;
    }

    public void delete(Long id) {
        LOG.debug("[CollectedSampleService] DELETE - start. id={}", id);
        repository.deleteById(id);
        LOG.debug("[CollectedSampleService] DELETE - done. id={}", id);
    }
}
