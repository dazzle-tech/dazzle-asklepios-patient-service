
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ExternalTest;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.ExternalTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.sendtest.ExternalTestDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExternalTestService {

    private final ExternalTestRepository externalTestRepository;
    private final DiagnosticOrderTestRepository diagnosticTestRepository;

    public ExternalTestService(
            ExternalTestRepository externalTestRepository,
            DiagnosticOrderTestRepository diagnosticTestRepository
    ) {
        this.externalTestRepository = externalTestRepository;
        this.diagnosticTestRepository = diagnosticTestRepository;
    }

    public ExternalTest create(ExternalTestDTO dto) {
        // validate diagnostic_test exists
        if (!diagnosticTestRepository.existsById(dto.testId())) {
            throw new IllegalArgumentException("DiagnosticTest not found with id " + dto.testId());
        }

        ExternalTest e = new ExternalTest();
        e.setTestId(dto.testId());
        e.setFacilityName(dto.facilityName());
        e.setReason(dto.reason());

        try {
            return externalTestRepository.save(e);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(test_id)
            throw new IllegalArgumentException("ExternalTest already exists for testId " + dto.testId());
        }
    }

    @Transactional(readOnly = true)
    public ExternalTest getByTestId(Long testId) {
        return externalTestRepository.findByTestId(testId)
                .orElseThrow(() -> new IllegalArgumentException("ExternalTest not found for testId " + testId));
    }

    public void deleteByTestId(Long testId) {
        if (!externalTestRepository.existsByTestId(testId)) {
            throw new IllegalArgumentException("ExternalTest not found for testId " + testId);
        }
        externalTestRepository.deleteByTestId(testId);
    }

    @Transactional(readOnly = true)
    public boolean isSentToExternal(Long testId) {
        return externalTestRepository.existsByTestId(testId);
    }
}
