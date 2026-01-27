package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;
    private final PatientInsuranceCoverageRepository patientInsuranceCoverageRepository;

    public PatientInsuranceService(
            PatientInsuranceRepository patientInsuranceRepository,
            EntityManager entityManager,
            PatientInsuranceCoverageRepository patientInsuranceCoverageRepository) {
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.entityManager = entityManager;
        this.patientInsuranceCoverageRepository = patientInsuranceCoverageRepository;
    }

    public PatientInsurance create(PatientInsuranceCreateDTO dto) {
        LOG.info("[CREATE] PatientInsurance payload={}", dto);

        PatientInsurance entity = PatientInsurance.builder()
                .patient(refPatient(dto.patientId()))
                .payorId(dto.payorId())
                .planId(dto.planId())
                .policyHolderId(dto.policyHolderId())
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
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] PatientInsurance failed (unexpected) payload={}", dto, ex);
            throw ex;
        }
    }

    public PatientInsurance update(
            PatientInsurance existing,
            PatientInsuranceUpdateDTO dto
    ) {
        Long existingId = existing != null ? existing.getId() : null;
        LOG.info("[UPDATE] PatientInsurance id={} payload={}", existingId, dto);

        existing.setPatient(refPatient(dto.patientId()));
        existing.setPayorId(dto.payorId());
        existing.setPlanId(dto.planId());
        existing.setPolicyHolderId(dto.policyHolderId());
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
                    existingId, dto.patientId(), dto.payorId(), dto.planId(), dto, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[UPDATE] PatientInsurance failed (unexpected) id={} payload={}", existingId, dto, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientInsurance> findAll(Pageable pageable) {
        LOG.debug("[FIND_ALL] PatientInsurance pageable={}", pageable);
        Page<PatientInsurance> page = patientInsuranceRepository.findAll(pageable);
        LOG.debug("[FIND_ALL] PatientInsurance result totalElements={} totalPages={} pageNumber={} pageSize={}",
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
        return page;
    }

    @Transactional(readOnly = true)
    public Page<PatientInsurance> getInsurancesByPatient(Long patientId, Pageable pageable) {
        LOG.debug("[FIND_BY_PATIENT] PatientInsurance patientId={} pageable={}", patientId, pageable);

        Page<PatientInsurance> page = patientInsuranceRepository.findByPatientId(patientId, pageable);
        LOG.debug("[FIND_BY_PATIENT] PatientInsurance result patientId={} totalElements={} totalPages={} pageNumber={} pageSize={}",
                patientId, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
        return page;
    }

    @Transactional(readOnly = true)
    public long countCoverages(Long insuranceId) {
        LOG.debug("[COUNT] insuranceId={} → counting coverages", insuranceId);
        return patientInsuranceCoverageRepository.countByInsuranceId(insuranceId);
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
        LOG.debug("[REF_PATIENT] patientId={}", patientId);
        return entityManager.getReference(Patient.class, patientId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientInsurance constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("ux_patient_insurance_one_primary_per_patient")) {
            LOG.warn("[DB_CONSTRAINT] PatientInsurance one primary per patient violated");
            return new BadRequestAlertException(
                    "This patient already has a primary insurance.",
                    "patientInsurance",
                    "primary.exists"
            );
        }

        if (messageLower.contains("ux_patient_insurance_patient_payor")) {
            LOG.warn("[DB_CONSTRAINT] PatientInsurance duplicate patient/payor violated");
            return new BadRequestAlertException(
                    "This patient already has an insurance for the selected payor.",
                    "patientInsurance",
                    "patient.payor.duplicate"
            );
        }

        LOG.warn("[DB_CONSTRAINT] PatientInsurance unknown constraint violated");
        return new BadRequestAlertException(
                "Database constraint violated while saving patient insurance.",
                "patientInsurance",
                "db.constraint"
        );
    }

    @Transactional(readOnly = true)
    public PatientInsurance findById(Long id) {
        LOG.debug("[FIND_BY_ID] PatientInsurance id={}", id);

        PatientInsurance entity = patientInsuranceRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[FIND_BY_ID] PatientInsurance not found id={}", id);
                    return new NotFoundAlertException(
                            "PatientInsurance not found with id " + id,
                            "patientInsurance",
                            "notfound"
                    );
                });

        LOG.debug("[FIND_BY_ID] PatientInsurance found id={} patientId={} payorId={} planId={} isPrimary={}",
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getPayorId(),
                entity.getPlanId(),
                entity.getIsPrimary()
        );

        return entity;
    }
}
