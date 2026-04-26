package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisCreateDTO;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisUpdateDTO;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientDiagnosisService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientDiagnosisService.class);

    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final PatientRepository patientRepository;

    public PatientDiagnosisService(
            PatientDiagnosisRepository patientDiagnosisRepository,
            PatientRepository patientRepository
    ) {
        this.patientDiagnosisRepository = patientDiagnosisRepository;
        this.patientRepository = patientRepository;
    }

    public PatientDiagnosis create(PatientDiagnosisCreateDTO dto) {
        LOG.info("[CREATE] Request to create PatientDiagnosis payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        PatientDiagnosis entity = PatientDiagnosis.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .diagnosisId(dto.diagnosisId())
                .type(dto.type())
                .suspected(dto.suspected())
                .major(dto.major())
                .build();

        try {
            return patientDiagnosisRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving patient diagnosis.",
                    "patientDiagnosis",
                    "db.constraint"
            );
        }
    }

    public PatientDiagnosis update(Long id, PatientDiagnosisUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update PatientDiagnosis id={} payload={}", id, dto);

        PatientDiagnosis existing = patientDiagnosisRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientDiagnosis not found with id " + id,
                        "patientDiagnosis",
                        "notfound"
                ));

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        existing.setPatient(patient);
        existing.setEncounterId(dto.encounterId());
        existing.setDiagnosisId(dto.diagnosisId());
        existing.setType(dto.type());
        existing.setSuspected(dto.suspected());
        existing.setMajor(dto.major());
        existing.setLastModifiedDate(Instant.now());

        try {
            return patientDiagnosisRepository.saveAndFlush(existing);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient diagnosis.",
                    "patientDiagnosis",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<PatientDiagnosis> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND LATEST] encounterId={}", encounterId);

        return patientDiagnosisRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public Page<PatientDiagnosis> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[FIND PAGE] patientId={} pageable={}", patientId, pageable);
        return patientDiagnosisRepository.findByPatient_IdOrderByCreatedDateDesc(patientId, pageable);
    }


    @Transactional(readOnly = true)
    public List<PatientDiagnosis> getByEncounterId(Long encounterId) {
        LOG.debug("[GET_DIAGNOSIS_BY_ENCOUNTER] encounterId={}", encounterId);
        return patientDiagnosisRepository.findByEncounterId(encounterId);
    }
    @Transactional(readOnly = true)
    public Optional<PatientDiagnosis> getPrimaryDiagnosisByEncounterId(Long encounterId) {
        LOG.debug("[GET_PRIMARY_DIAGNOSIS_BY_ENCOUNTER] encounterId={}", encounterId);

        return patientDiagnosisRepository
                .findByEncounterIdAndType(encounterId, DiagnosisType.PRIMARY);
    }
    public void hardDelete(Long id) {
        LOG.warn("[HARD_DELETE] Request to permanently delete PatientDiagnosis id={}", id);

        PatientDiagnosis entity = patientDiagnosisRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientDiagnosis not found with id " + id,
                        "patientDiagnosis",
                        "notfound"
                ));

        patientDiagnosisRepository.delete(entity);

        LOG.info("[HARD_DELETE] PatientDiagnosis deleted permanently id={}", id);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("fk_patient_diagnosis_patient")) {
            throw new BadRequestAlertException(
                    "Invalid patient_id (patient does not exist).",
                    "patientDiagnosis",
                    "fk.patient"
            );
        }

        if (lower.contains("fk_patient_diagnosis_icd_diagnosis")) {
            throw new BadRequestAlertException(
                    "Invalid diagnosis_id (diagnosis does not exist).",
                    "patientDiagnosis",
                    "fk.diagnosis"
            );
        }

        if (lower.contains("ux_patient_diagnosis_diag_type_flags_patient_encounter")) {
            throw new BadRequestAlertException(
                    "This diagnosis already exists for the same patient and encounter with the same attributes.",
                    "patientDiagnosis",
                    "duplicate"
            );
        }

        if (lower.contains("ux_patient_diagnosis_primary_per_encounter")) {
            throw new BadRequestAlertException(
                    "Only one PRIMARY diagnosis is allowed per encounter.",
                    "patientDiagnosis",
                    "primary.already.exists"
            );
        }

        if (lower.contains("not-null") || lower.contains("null value")) {
            throw new BadRequestAlertException(
                    "Required fields are missing.",
                    "patientDiagnosis",
                    "required.fields"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving patient diagnosis.",
                "patientDiagnosis",
                "db.constraint"
        );
    }
    @Transactional(readOnly = true)
    public boolean existsByEncounterId(Long encounterId) {
        LOG.debug("[EXISTS CHECK] encounterId={}", encounterId);

        return patientDiagnosisRepository.existsByEncounterId(encounterId);
    }

    public Set<PatientDiagnosis> findPrimaryByEncounterIds(List<Long> encounterIds) {
        return patientDiagnosisRepository
                .findDistinctByEncounterIdInAndType(encounterIds, DiagnosisType.PRIMARY);
    }
}
