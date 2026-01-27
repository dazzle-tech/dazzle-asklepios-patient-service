package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.dazzle.asklepios.repository.PatientPreferredHealthProfessionalRepository;
import com.dazzle.asklepios.service.dto.patientPreferredHealthProfessional.PatientPreferredHealthProfessionalCreateDTO;
import com.dazzle.asklepios.service.dto.patientPreferredHealthProfessional.PatientPreferredHealthProfessionalUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientPreferredHealthProfessionalService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPreferredHealthProfessionalService.class);

    private final PatientPreferredHealthProfessionalRepository preferredRepository;

    public PatientPreferredHealthProfessionalService(
            PatientPreferredHealthProfessionalRepository preferredRepository) {
        this.preferredRepository = preferredRepository;
    }

    @Transactional(readOnly = true)
    public Page<PatientPreferredHealthProfessional> findAllByPatient(Long patientId, Pageable pageable) {
        LOG.debug("Fetching all PatientPreferredHealthProfessional for patientId={} with pageable={}", patientId, pageable);
        return preferredRepository.findByPatient_Id(patientId, pageable);
    }

    public PatientPreferredHealthProfessional create(Patient patient, PatientPreferredHealthProfessionalCreateDTO dto) {
        LOG.info("[CREATE] Request to create PatientPreferredHealthProfessional for patientId={} payload={}", patient.getId(), dto);
        try {
            PatientPreferredHealthProfessional entity = PatientPreferredHealthProfessional.builder()
                    .patient(patient)
                    .practitionerId(dto.practitionerId())
                    .networkAffiliation(dto.networkAffiliation())
                    .relatedWith(dto.relatedWith())
                    .build();

            PatientPreferredHealthProfessional saved = preferredRepository.saveAndFlush(entity);
            LOG.info("Successfully created PatientPreferredHealthProfessional id={} for patientId={}", saved.getId(), patient.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            handleConstraintsOnCreateOrUpdate(exception);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving PatientPreferredHealthProfessional.",
                    "patientPreferredHealthProfessional",
                    "db.constraint"
            );
        }
    }

    public PatientPreferredHealthProfessional update(PatientPreferredHealthProfessional existing, PatientPreferredHealthProfessionalUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update PatientPreferredHealthProfessional id={} payload={}", existing.getId(), dto);
        
        try {
            if (dto.practitionerId() != null) {
                existing.setPractitionerId(dto.practitionerId());
            }
            existing.setNetworkAffiliation(dto.networkAffiliation());
            existing.setRelatedWith(dto.relatedWith());

            PatientPreferredHealthProfessional saved = preferredRepository.saveAndFlush(existing);
            LOG.info("Successfully updated PatientPreferredHealthProfessional id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            handleConstraintsOnCreateOrUpdate(exception);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating PatientPreferredHealthProfessional.",
                    "patientPreferredHealthProfessional",
                    "db.constraint"
            );
        }
    }
    @Transactional(readOnly = true)
    public PatientPreferredHealthProfessional findByIdOrThrow(Long id) {
        LOG.debug("[FIND BY ID] Fetching PatientPreferredHealthProfessional id={}", id);

        return preferredRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[FIND BY ID] PatientPreferredHealthProfessional not found id={}", id);
                    return new NotFoundAlertException(
                            "PatientPreferredHealthProfessional not found with id " + id,
                            "patientPreferredHealthProfessional",
                            "notfound"
                    );
                });
    }


    @Transactional
    public void hardDelete(Long id) {
        LOG.debug("[DELETE] Hard delete PatientPreferredHealthProfessional id={}", id);

        try {
            preferredRepository.deleteById(id);
            LOG.info("Successfully hard deleted PatientPreferredHealthProfessional id={}", id);

        } catch (EmptyResultDataAccessException ex) {
            throw new BadRequestAlertException(
                    "Mapping not found",
                    "patientPreferredHealthProfessional",
                    "notfound"
            );
        }
    }


    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation while saving PatientPreferredHealthProfessional: {}", message, exception);

        if (lower.contains("uk_pphp_patient_practitioner")
                || lower.contains("unique constraint")
                || lower.contains("duplicate key")
                || lower.contains("duplicate entry")) {

            throw new BadRequestAlertException(
                    "This patient already has a preferred health professional with the same practitioner.",
                    "patientPreferredHealthProfessional",
                    "unique.patient_practitioner"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving PatientPreferredHealthProfessional.",
                "patientPreferredHealthProfessional",
                "db.constraint"
        );
    }
}
