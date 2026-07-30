package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceUpdateDTO;
import com.dazzle.asklepios.service.helper.PayorHelper;
import com.dazzle.asklepios.service.helper.PayorPlanHelper;
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
public class PatientInsuranceService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceService.class);

    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientInsuranceCoverageRepository patientInsuranceCoverageRepository;
    private final PatientRepository patientRepository;
    private final PayorHelper payorHelper;
    private final PayorPlanHelper payorPlanHelper;

    public PatientInsuranceService(
            PatientInsuranceRepository patientInsuranceRepository,
            PatientInsuranceCoverageRepository patientInsuranceCoverageRepository,
            PatientRepository patientRepository, PayorHelper payorHelper, PayorPlanHelper payorPlanHelper) {
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientInsuranceCoverageRepository = patientInsuranceCoverageRepository;
        this.patientRepository = patientRepository;
        this.payorHelper = payorHelper;
        this.payorPlanHelper = payorPlanHelper;
    }

    public PatientInsurance create(PatientInsuranceCreateDTO dto) {
        LOG.info("[CREATE] PatientInsurance payload={}", dto);

        payorHelper.validatePayorExists(dto.payorId());
        payorPlanHelper.validatePayorPlanExists(dto.planId());

        PatientInsurance entity = PatientInsurance.builder()
                .patient(refPatient(dto.patientId()))
                .payorId(dto.payorId())
                .planId(dto.planId())
                .policyHolderId(dto.policyHolderId() == null ? null : refPatient(dto.policyHolderId()).getId())
                .policyNumber(dto.policyNumber())
                .groupNumber(dto.groupNumber())
                .expirationDate(dto.expirationDate())
                .remainingBenefits(dto.remainingBenefits())
                .remainingDeductibles(dto.remainingDeductibles())
                .isPrimary(Boolean.TRUE.equals(dto.isPrimary()))
                .build();

        try {
            PatientInsurance saved = patientInsuranceRepository.saveAndFlush(entity);
            LOG.info("[CREATE] PatientInsurance success id={} patientId={} payorId={} planId={} isPrimary={}",
                    saved.getId(), dto.patientId(), dto.payorId(), dto.planId(), Boolean.TRUE.equals(dto.isPrimary()));
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientInsurance failed (constraint) patientId={} payorId={} planId={} payload={}",
                    dto.patientId(), dto.payorId(), dto.planId(), dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

// ===================== PatientInsuranceService =====================

    @Transactional
    public PatientInsurance update(Long id, PatientInsuranceUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update PatientInsurance id={} payload={}", id, dto);

        PatientInsurance existing = patientInsuranceRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientInsurance not found with id " + id,
                        "patientInsurance",
                        "notfound"
                ));
        payorHelper.validatePayorExists(dto.payorId());
        payorPlanHelper.validatePayorPlanExists(dto.planId());

        existing.setPatient(refPatient(dto.patientId()));
        existing.setPayorId(dto.payorId());
        existing.setPlanId(dto.planId());
        existing.setPolicyHolderId(dto.policyHolderId() == null ? null : refPatient(dto.policyHolderId()).getId());
        existing.setPolicyNumber(dto.policyNumber());
        existing.setGroupNumber(dto.groupNumber());
        existing.setExpirationDate(dto.expirationDate());
        existing.setRemainingBenefits(dto.remainingBenefits());
        existing.setRemainingDeductibles(dto.remainingDeductibles());
        existing.setIsPrimary(Boolean.TRUE.equals(dto.isPrimary()));

        try {
            PatientInsurance saved = patientInsuranceRepository.saveAndFlush(existing);
            LOG.info("[UPDATE] PatientInsurance success id={} patientId={} payorId={} planId={} isPrimary={}",
                    saved.getId(), dto.patientId(), dto.payorId(), dto.planId(), Boolean.TRUE.equals(dto.isPrimary()));
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientInsurance failed (constraint) id={} patientId={} payorId={} planId={} payload={}",
                    id, dto.patientId(), dto.payorId(), dto.planId(), dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientInsurance> getInsurancesByPatient(Long patientId, Pageable pageable) {
        LOG.debug("[FIND_BY_PATIENT] PatientInsurance patientId={} pageable={}", patientId, pageable);
        return patientInsuranceRepository.findByPatientId(patientId, pageable);
    }


    @Transactional(readOnly = true)
    public Page<PatientInsurance> findAll(Pageable pageable) {
        LOG.debug("[FIND_ALL] PatientInsurance pageable={}", pageable);
        return patientInsuranceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public long countCoverages(Long insuranceId) {
        LOG.debug("[COUNT] insuranceId={} → counting coverages", insuranceId);
        return patientInsuranceCoverageRepository.countByInsuranceId(insuranceId);
    }

    @Transactional(readOnly = true)
    public PatientInsurance findById(Long id) {
        LOG.debug("[FIND_BY_ID] PatientInsurance id={}", id);

        return patientInsuranceRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientInsurance not found with id " + id,
                        "patientInsurance",
                        "notfound"
                ));
    }

    public boolean delete(Long id, boolean deleteCoverages) {
        LOG.info("[DELETE] PatientInsurance id={} deleteCoverages={}", id, deleteCoverages);

        if (id == null || !patientInsuranceRepository.existsById(id)) {
            LOG.warn("[DELETE] PatientInsurance not found or invalid id={}", id);
            return false;
        }

        long count = patientInsuranceCoverageRepository.countByInsuranceId(id);
        LOG.info("[DELETE] PatientInsurance id={} coveragesCount={}", id, count);

        if (count > 0 && !deleteCoverages) {
            throw new BadRequestAlertException(
                    "Cannot delete insurance because it has coverages.",
                    "patientInsurance",
                    "delete.hasCoverages"
            );
        }

        try {
            if (deleteCoverages && count > 0) {
                patientInsuranceCoverageRepository.deleteByInsuranceId(id);
                LOG.info("[DELETE] PatientInsurance coverages deleted insuranceId={} count={}", id, count);
            }
            patientInsuranceRepository.deleteById(id);
            patientInsuranceRepository.flush();
            LOG.info("[DELETE] PatientInsurance success id={}", id);
            return true;

        } catch (DataIntegrityViolationException ex) {
            LOG.error("[DELETE] PatientInsurance failed (FK) id={}", id, ex);
            throw new BadRequestAlertException(
                    "Cannot delete insurance because it has coverages.",
                    "patientInsurance",
                    "delete.hasCoverages"
            );
        }
    }

    private Patient refPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "patient",
                        "notfound"
                ));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientInsurance constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("ux_patient_insurance_one_primary_per_patient")) {
            return new BadRequestAlertException(
                    "This patient already has a primary insurance.",
                    "patientInsurance",
                    "primary.exists"
            );
        }

        if (messageLower.contains("ux_patient_insurance_patient_payor")) {
            return new BadRequestAlertException(
                    "This patient already has an insurance for the selected payor.",
                    "patientInsurance",
                    "patient.payor.duplicate"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient insurance.",
                "patientInsurance",
                "db.constraint"
        );
    }
}
