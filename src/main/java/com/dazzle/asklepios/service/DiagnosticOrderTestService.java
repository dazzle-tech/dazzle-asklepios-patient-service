package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestService.class);

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    public DiagnosticOrderTestService(DiagnosticOrderTestRepository diagnosticOrderTestRepository, DiagnosticOrderStatusService diagnosticOrderStatusService) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    public DiagnosticOrderTest create(DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("Request to create DiagnosticOrderTest: {}", dto);

        DiagnosticOrderTest t = new DiagnosticOrderTest();
        t.setPatientId(dto.patientId());
        t.setEncounterId(dto.encounterId());
        t.setOrderId(dto.orderId());
        t.setTestId(dto.testId());

        t.setStatus(DiagnosticOrderTestStatus.NEW);

        if (dto.processingStatus() != null) {
            t.setProcessingStatus(dto.processingStatus());
        } else {
            t.setProcessingStatus(DiagnosticStatus.NEW);
        }

        t.setReceivedDepartmentId(dto.receivedDepartmentId());
        t.setReason(dto.reason());
        t.setNotes(dto.notes());
        t.setSubmitDate(dto.submitDate());
        t.setOrderType(dto.orderType());

        t.setFromDepartmentId(dto.fromDepartmentId());
        t.setFromFacilityId(dto.fromFacilityId());
        t.setToFacilityId(dto.toFacilityId());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(t);

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }


    public DiagnosticOrderTest update(DiagnosticOrderTest existing, DiagnosticOrderTestUpdateDTO dto) {
        LOG.debug("Request to update DiagnosticOrderTest id={} payload={}", existing.getId(), dto);

        existing.setPatientId(dto.patientId());
        existing.setEncounterId(dto.encounterId());
        existing.setOrderId(dto.orderId());
        existing.setTestId(dto.testId());

        existing.setReceivedDepartmentId(dto.receivedDepartmentId());
        existing.setReason(dto.reason());
        existing.setNotes(dto.notes());

        existing.setFromDepartmentId(dto.fromDepartmentId());
        existing.setFromFacilityId(dto.fromFacilityId());
        existing.setToFacilityId(dto.toFacilityId());



        return diagnosticOrderTestRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTest> findByOrderIdFilterStatus(
            Long orderId,
            DiagnosticOrderTestStatus status,
            List<DiagnosticOrderTestStatus> excludeStatuses,
            Pageable pageable
    ) {
        if (status != null) {
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
