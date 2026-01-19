package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import com.dazzle.asklepios.repository.DiagnosticOrderTestCollectedSampleRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkSameDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestCollectedSampleService {

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

        return saved;
    }

    public List<DiagnosticOrderTestCollectedSample> bulkCreateSame(
            DiagnosticOrderTestCollectedSampleBulkSameDTO dto
    ) {

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

        return saved;
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
