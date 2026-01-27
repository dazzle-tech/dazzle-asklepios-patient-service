package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientInsuranceCoverageService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceCoverageService.class);

    private final PatientInsuranceCoverageRepository coverageRepository;
    private final EntityManager entityManager;

    public PatientInsuranceCoverageService(
            PatientInsuranceCoverageRepository coverageRepository,
            EntityManager entityManager
    ) {
        this.coverageRepository = coverageRepository;
        this.entityManager = entityManager;
    }

    public PatientInsuranceCoverage create(PatientInsuranceCoverageCreateDTO dto) {
        LOG.info("[CREATE] PatientInsuranceCoverage payload={}", dto);

        PatientInsuranceCoverage entity = PatientInsuranceCoverage.builder()
                .insurance(refInsurance(dto.insuranceId()))
                .itemType(dto.itemType())
                .coverageType(dto.coverageType())
                .amount(dto.amount())
                .build();

        try {
            return coverageRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public PatientInsuranceCoverage findByIdOrThrow(Long id) {
        LOG.debug("[FIND BY ID] Fetching PatientInsuranceCoverage id={}", id);

        return coverageRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientInsuranceCoverage not found with id " + id,
                        "patientInsuranceCoverage",
                        "notfound"
                ));
    }

    public PatientInsuranceCoverage update(
            PatientInsuranceCoverage existing,
            PatientInsuranceCoverageUpdateDTO dto
    ) {
        LOG.info("[UPDATE] PatientInsuranceCoverage id={} payload={}", existing.getId(), dto);

        existing.setInsurance(refInsurance(dto.insuranceId()));
        existing.setItemType(dto.itemType());
        existing.setCoverageType(dto.coverageType());
        existing.setAmount(dto.amount());

        try {
            return coverageRepository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }


    @Transactional(readOnly = true)
    public Page<PatientInsuranceCoverage> findAllByInsurance(Long insuranceId, org.springframework.data.domain.Pageable pageable) {

        return coverageRepository.findByInsuranceId(insuranceId, pageable);
    }

    public boolean delete(Long id) {
        if (id == null || !coverageRepository.existsById(id)) {
            return false;
        }
        coverageRepository.deleteById(id);
        return true;
    }

    private PatientInsurance refInsurance(Long insuranceId) {
        return entityManager.getReference(PatientInsurance.class, insuranceId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        if (lower.contains("ux_insurance_coverage_ins_item_cov")) {
            return new BadRequestAlertException(
                    "Duplicate coverage for the same insurance, item type and coverage type.",
                    "patientInsuranceCoverage",
                    "duplicate"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving insurance coverage.",
                "patientInsuranceCoverage",
                "db.constraint"
        );
    }
}
