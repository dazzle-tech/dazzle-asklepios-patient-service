// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DiagnosticOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderService.class);

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    public DiagnosticOrderService(DiagnosticOrderRepository diagnosticOrderRepository, DiagnosticOrderTestRepository diagnosticOrderTestRepository) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    public DiagnosticOrder create(DiagnosticOrderCreateDTO dto) {
        LOG.debug("Request to create DiagnosticOrder: {}", dto);

        DiagnosticOrder o = new DiagnosticOrder();
        o.setPatientId(dto.patientId());
        o.setEncounterId(dto.encounterId());

        // defaults
        o.setStatus(dto.status() != null ? dto.status() : DiagnosticStatus.NEW);
        o.setLabStatus(dto.labStatus() != null ? dto.labStatus() : DiagnosticStatus.NEW);
        o.setRadStatus(dto.radStatus() != null ? dto.radStatus() : DiagnosticStatus.NEW);
        o.setSubmittedBy(dto.submittedBy());
        o.setSubmittedDate(dto.submittedDate());
        o.setIsUrgent(dto.isUrgent() != null ? dto.isUrgent() : false);

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

    public DiagnosticOrder submit(DiagnosticOrder existing, String submittedBy) {
        existing.setSaveDraft(false);
        existing.setStatus(DiagnosticStatus.SUBMITTED);
        existing.setSubmittedBy(submittedBy);
        existing.setSubmittedDate(Instant.now());

        DiagnosticOrder saved = diagnosticOrderRepository.save(existing);


        diagnosticOrderTestRepository.bulkUpdateStatusForOrder(
                saved.getId(),
           DiagnosticOrderTestStatus.SUBMITTED
        );

        return saved;
    }
    public void delete(Long id) {
        diagnosticOrderRepository.deleteById(id);
    }
}
