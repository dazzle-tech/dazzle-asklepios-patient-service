package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.dazzle.asklepios.repository.PatientPreferredHealthProfessionalRepository;
import com.dazzle.asklepios.repository.PatientRepository;
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
public class PatientPreferredHealthProfessionalService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPreferredHealthProfessionalService.class);

    private final PatientPreferredHealthProfessionalRepository preferredRepository;
    private final PatientRepository patientRepository;

    public PatientPreferredHealthProfessionalService(
            PatientPreferredHealthProfessionalRepository preferredRepository,
            PatientRepository patientRepository
    ) {
        this.preferredRepository = preferredRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public Page<PatientPreferredHealthProfessional> findAllByPatient(Long patientId, Pageable pageable) {
        LOG.debug("Fetching all PatientPreferredHealthProfessional for patientId={} with pageable={}", patientId, pageable);
        return preferredRepository.findByPatient_Id(patientId, pageable);
    }

    public PatientPreferredHealthProfessional create(Long patientId, PatientPreferredHealthProfessional preferredRequest) {
        LOG.info("[CREATE] Request to create PatientPreferredHealthProfessional for patientId={} payload={}", patientId, preferredRequest);

        if (preferredRequest == null) {
            throw new BadRequestAlertException(
                    "PatientPreferredHealthProfessional payload is required",
                    "patientPreferredHealthProfessional",
                    "payload.required"
            );
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> {
                    LOG.error("Patient not found with id={}", patientId);
                    return new NotFoundAlertException(
                            "Patient not found with id " + patientId,
                            "patient",
                            "notfound"
                    );
                });

        try {
            PatientPreferredHealthProfessional entity = PatientPreferredHealthProfessional.builder()
                    .patient(patient)
                    .practitionerId(preferredRequest.getPractitionerId())
                    .facilityId(preferredRequest.getFacilityId())
                    .networkAffiliation(preferredRequest.getNetworkAffiliation())
                    .relatedWith(preferredRequest.getRelatedWith())
                    .build();

            PatientPreferredHealthProfessional saved = preferredRepository.saveAndFlush(entity);
            LOG.info("Successfully created PatientPreferredHealthProfessional id={} for patientId={}", saved.getId(), patientId);
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

    public PatientPreferredHealthProfessional update(Long id, PatientPreferredHealthProfessional preferredRequest) {
        LOG.info("[UPDATE] Request to update PatientPreferredHealthProfessional id={} payload={}", id, preferredRequest);

        if (preferredRequest == null) {
            throw new BadRequestAlertException(
                    "PatientPreferredHealthProfessional payload is required",
                    "patientPreferredHealthProfessional",
                    "payload.required"
            );
        }

        PatientPreferredHealthProfessional existing = preferredRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("PatientPreferredHealthProfessional not found with id={}", id);
                    return new NotFoundAlertException(
                            "PatientPreferredHealthProfessional not found with id " + id,
                            "patientPreferredHealthProfessional",
                            "notfound"
                    );
                });

        try {
            existing.setPractitionerId(preferredRequest.getPractitionerId());
            existing.setFacilityId(preferredRequest.getFacilityId());
            existing.setNetworkAffiliation(preferredRequest.getNetworkAffiliation());
            existing.setRelatedWith(preferredRequest.getRelatedWith());

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

    @Transactional
    public void hardDelete(Long id) {
        LOG.debug("[DELETE] Request to hard delete PatientPreferredHealthProfessional id={}", id);

        PatientPreferredHealthProfessional target = preferredRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("Mapping not found for id={}", id);
                    return new BadRequestAlertException(
                            "Mapping not found",
                            "patientPreferredHealthProfessional",
                            "notfound"
                    );
                });

        preferredRepository.delete(target);
        LOG.info("Successfully hard deleted PatientPreferredHealthProfessional id={}", id);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation while saving PatientPreferredHealthProfessional: {}", message, exception);

        if (lower.contains("uk_pphp_patient_practitioner_facility")
                || lower.contains("unique constraint")
                || lower.contains("duplicate key")
                || lower.contains("duplicate entry")) {

            throw new BadRequestAlertException(
                    "This patient already has a preferred health professional with the same practitioner and facility.",
                    "patientPreferredHealthProfessional",
                    "unique.patient_practitioner_facility"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving PatientPreferredHealthProfessional.",
                "patientPreferredHealthProfessional",
                "db.constraint"
        );
    }
}
