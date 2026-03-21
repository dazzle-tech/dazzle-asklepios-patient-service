package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientInsuranceCoverageService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceCoverageService.class);

    private final PatientInsuranceCoverageRepository coverageRepository;
    private final PatientInsuranceService patientInsuranceService;

    public PatientInsuranceCoverageService(
            PatientInsuranceCoverageRepository coverageRepository,
            PatientInsuranceService patientInsuranceService
    ) {
        this.coverageRepository = coverageRepository;
        this.patientInsuranceService = patientInsuranceService;
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
            PatientInsuranceCoverage saved = coverageRepository.saveAndFlush(entity);
            LOG.info("[CREATE] PatientInsuranceCoverage success id={} insuranceId={} itemType={} coverageType={}",
                    saved.getId(), dto.insuranceId(), dto.itemType(), dto.coverageType());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientInsuranceCoverage failed (constraint) insuranceId={} payload={}",
                    dto.insuranceId(), dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public PatientInsuranceCoverage findByIdOrThrow(Long id) {
        LOG.debug("[FIND_BY_ID] Fetching PatientInsuranceCoverage id={}", id);

        return coverageRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientInsuranceCoverage not found with id " + id,
                        "patientInsuranceCoverage",
                        "notfound"
                ));
    }

    public PatientInsuranceCoverage update(Long id, PatientInsuranceCoverageUpdateDTO dto) {
        LOG.info("[UPDATE] PatientInsuranceCoverage id={} payload={}", id, dto);

        PatientInsuranceCoverage existing = findByIdOrThrow(id);

        existing.setInsurance(refInsurance(dto.insuranceId()));
        existing.setItemType(dto.itemType());
        existing.setCoverageType(dto.coverageType());
        existing.setAmount(dto.amount());

        try {
            PatientInsuranceCoverage saved = coverageRepository.saveAndFlush(existing);
            LOG.info("[UPDATE] PatientInsuranceCoverage success id={} insuranceId={} itemType={} coverageType={}",
                    saved.getId(), dto.insuranceId(), dto.itemType(), dto.coverageType());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientInsuranceCoverage failed (constraint) id={} insuranceId={} payload={}",
                    id, dto.insuranceId(), dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientInsuranceCoverage> findAllByInsurance(Long insuranceId, Pageable pageable) {
        LOG.debug("[FIND_BY_INSURANCE] insuranceId={} pageable={}", insuranceId, pageable);
        return coverageRepository.findByInsuranceId(insuranceId, pageable);
    }

    public void delete(Long id) {
        LOG.info("[DELETE] PatientInsuranceCoverage id={}", id);
        coverageRepository.deleteById(id);
        coverageRepository.flush();
        LOG.info("[DELETE] PatientInsuranceCoverage success id={}", id);
    }

    private PatientInsurance refInsurance(Long insuranceId) {
        LOG.debug("[REF_INSURANCE] insuranceId={}", insuranceId);
        return patientInsuranceService.findById(insuranceId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientInsuranceCoverage constraint violated rootMessage={}", message, exception);

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
