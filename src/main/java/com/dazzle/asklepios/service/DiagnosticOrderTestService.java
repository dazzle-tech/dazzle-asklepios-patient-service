// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestService.class);

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticOrderTestService(DiagnosticOrderTestRepository diagnosticOrderTestRepository) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    public DiagnosticOrderTest create(DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("Request to create DiagnosticOrderTest: {}", dto);

        DiagnosticOrderTest t = new DiagnosticOrderTest();
        t.setPatientId(dto.patientId());
        t.setEncounterId(dto.encounterId());
        t.setStatus(dto.status());
        t.setOrderId(dto.orderId());
        t.setTestId(dto.testId());

        t.setReceivedDepartmentId(dto.receivedDepartmentId());
        t.setReason(dto.reason());
        t.setNotes(dto.notes());
        t.setProcessingStatus(dto.processingStatus());

        t.setSubmitDate(dto.submitDate());
        t.setAcceptedDate(dto.acceptedDate());
        t.setRejectedDate(dto.rejectedDate());
        t.setPatientArrivedDate(dto.patientArrivedDate());
        t.setReadyDate(dto.readyDate());
        t.setApprovedDate(dto.approvedDate());

        t.setOrderType(dto.orderType());
        t.setAcceptedBy(dto.acceptedBy());
        t.setRejectedBy(dto.rejectedBy());
        t.setRejectedReason(dto.rejectedReason());
        t.setPatientArrivedNoteRad(dto.patientArrivedNoteRad());
        t.setCancellationReason(dto.cancellationReason());

        t.setFromDepartmentId(dto.fromDepartmentId());
        t.setFromFacilityId(dto.fromFacilityId());
        t.setToFacilityId(dto.toFacilityId());

        // isActive removed from filtering; keep field if DB still has it, but don't rely on it.
        t.setIsActive(dto.isActive() != null ? dto.isActive() : true);

        return diagnosticOrderTestRepository.save(t);
    }

    public DiagnosticOrderTest update(DiagnosticOrderTest existing, DiagnosticOrderTestUpdateDTO dto) {
        LOG.debug("Request to update DiagnosticOrderTest id={} payload={}", existing.getId(), dto);

        existing.setPatientId(dto.patientId());
        existing.setEncounterId(dto.encounterId());
        existing.setStatus(dto.status());
        existing.setOrderId(dto.orderId());
        existing.setTestId(dto.testId());

        existing.setReceivedDepartmentId(dto.receivedDepartmentId());
        existing.setReason(dto.reason());
        existing.setNotes(dto.notes());
        existing.setProcessingStatus(dto.processingStatus());

        existing.setSubmitDate(dto.submitDate());
        existing.setAcceptedDate(dto.acceptedDate());
        existing.setRejectedDate(dto.rejectedDate());
        existing.setPatientArrivedDate(dto.patientArrivedDate());
        existing.setReadyDate(dto.readyDate());
        existing.setApprovedDate(dto.approvedDate());

        existing.setOrderType(dto.orderType());
        existing.setAcceptedBy(dto.acceptedBy());
        existing.setRejectedBy(dto.rejectedBy());
        existing.setRejectedReason(dto.rejectedReason());
        existing.setPatientArrivedNoteRad(dto.patientArrivedNoteRad());
        existing.setCancellationReason(dto.cancellationReason());

        existing.setFromDepartmentId(dto.fromDepartmentId());
        existing.setFromFacilityId(dto.fromFacilityId());
        existing.setToFacilityId(dto.toFacilityId());

        if (dto.isActive() != null) existing.setIsActive(dto.isActive());

        return diagnosticOrderTestRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTest> findByOrderIdFilterStatus(
            Long orderId,
            String status,
            List<String> excludeStatuses,
            Pageable pageable
    ) {
        if (status != null && !status.isBlank()) {
            return diagnosticOrderTestRepository.findByOrderIdAndStatus(orderId, status, pageable);
        }
        if (excludeStatuses != null && !excludeStatuses.isEmpty()) {
            return diagnosticOrderTestRepository.findByOrderIdAndStatusNotIn(orderId, excludeStatuses, pageable);
        }
        return diagnosticOrderTestRepository.findByOrderId(orderId, pageable);
    }

    public void delete(Long id) {
        diagnosticOrderTestRepository.deleteById(id);
    }
}
