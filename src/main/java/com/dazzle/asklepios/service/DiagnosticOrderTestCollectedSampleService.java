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

    public DiagnosticOrderTestCollectedSampleService(DiagnosticOrderTestCollectedSampleRepository repository) {
        this.repository = repository;
    }

    public DiagnosticOrderTestCollectedSample create(DiagnosticOrderTestCollectedSampleDTO dto) {
        DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
        s.setOrderId(dto.orderId());
        s.setOrderTestId(dto.orderTestId());
        s.setUnit(dto.unit());
        s.setQuantity(dto.quantity());
        s.setCollectedAt(dto.collectedAt());
        return repository.save(s);
    }
    public List<DiagnosticOrderTestCollectedSample> bulkCreateSame(DiagnosticOrderTestCollectedSampleBulkSameDTO dto) {
        List<DiagnosticOrderTestCollectedSample> entities = dto.orderTestIds().stream().map(orderTestId -> {
            DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
            s.setOrderId(dto.orderId());
            s.setOrderTestId(orderTestId);
            s.setUnit(dto.unit());
            s.setQuantity(dto.quantity());
            s.setCollectedAt(dto.collectedAt());
            return s;
        }).toList();

        return repository.saveAll(entities);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
