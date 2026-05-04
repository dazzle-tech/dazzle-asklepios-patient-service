
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ExternalTest;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.ExternalTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.sendtest.ExternalTestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
            throw new BadRequestAlertException(
                    "notfound",
                    "external_tests",
                    "DiagnosticTest not found with id " + dto.testId()
            );
        }

        ExternalTest externalTest = new ExternalTest();
        externalTest.setTestId(dto.testId());
        externalTest.setFacilityName(dto.facilityName());
        externalTest.setReason(dto.reason());

        try {
            return externalTestRepository.save(externalTest);
        } catch (DataIntegrityViolationException ex) {

            throw new BadRequestAlertException(
                    "duplicate",
                    "external_tests",
                    "ExternalTest already exists for testId " + dto.testId()
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<ExternalTest> getByTestId(Long testId) {
        return externalTestRepository.findByTestId(testId);
    }

    public void deleteByTestId(Long testId) {
        externalTestRepository.deleteByTestId(testId);
    }

}
