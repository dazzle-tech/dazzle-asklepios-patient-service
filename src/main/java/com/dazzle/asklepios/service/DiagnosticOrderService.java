// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiagnosticOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderService.class);

    private final DiagnosticOrderRepository diagnosticOrderRepository;

    public DiagnosticOrderService(DiagnosticOrderRepository diagnosticOrderRepository) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
    }

    public DiagnosticOrder create(DiagnosticOrderCreateDTO dto) {
        LOG.debug("Request to create DiagnosticOrder: {}", dto);

        DiagnosticOrder o = new DiagnosticOrder();
        o.setPatientId(dto.patientId());
        o.setEncounterId(dto.encounterId());
        o.setStatus(dto.status());
        o.setSaveDraft(dto.saveDraft());
        o.setSubmittedBy(dto.submittedBy());
        o.setSubmittedDate(dto.submittedDate());
        o.setIsUrgent(dto.isUrgent() != null ? dto.isUrgent() : false);
        o.setLabStatus(dto.labStatus());
        o.setRadStatus(dto.radStatus());

        return diagnosticOrderRepository.save(o);
    }

    public DiagnosticOrder update(DiagnosticOrder existing, DiagnosticOrderUpdateDTO dto) {
        LOG.debug("Request to update DiagnosticOrder id={} payload={}", existing.getId(), dto);

        existing.setPatientId(dto.patientId());
        existing.setEncounterId(dto.encounterId());
        existing.setStatus(dto.status());
        existing.setSaveDraft(dto.saveDraft());
        existing.setSubmittedBy(dto.submittedBy());
        existing.setSubmittedDate(dto.submittedDate());
        if (dto.isUrgent() != null) existing.setIsUrgent(dto.isUrgent());
        existing.setLabStatus(dto.labStatus());
        existing.setRadStatus(dto.radStatus());

        return diagnosticOrderRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrder> findByEncounter(Long encounterId, String status, Pageable pageable) {
        return (status == null || status.isBlank())
                ? diagnosticOrderRepository.findByEncounterId(encounterId, pageable)
                : diagnosticOrderRepository.findByEncounterIdAndStatus(encounterId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrder> findByPatient(Long patientId, String status, Pageable pageable) {
        return (status == null || status.isBlank())
                ? diagnosticOrderRepository.findByPatientId(patientId, pageable)
                : diagnosticOrderRepository.findByPatientIdAndStatus(patientId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrder> findByPatientAndEncounter(Long patientId, Long encounterId, String status, Pageable pageable) {
        return (status == null || status.isBlank())
                ? diagnosticOrderRepository.findByPatientIdAndEncounterId(patientId, encounterId, pageable)
                : diagnosticOrderRepository.findByPatientIdAndEncounterIdAndStatus(patientId, encounterId, status, pageable);
    }

    public void delete(Long id) {
        diagnosticOrderRepository.deleteById(id);
    }
}
