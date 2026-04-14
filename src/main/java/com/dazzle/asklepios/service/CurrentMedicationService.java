package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.CurrentMedication;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.CurrentMedicationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.currentMedication.CurrentMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.currentMedication.CurrentMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional
public class CurrentMedicationService {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentMedicationService.class);

    private final CurrentMedicationRepository currentMedicationRepository;
    private final PatientRepository patientRepository;

    private Patient getPatientOrThrow(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "currentMedication",
                        "patient.notfound"
                ));
    }

    public CurrentMedication create(CurrentMedicationCreateDTO dto) {
        LOG.info("[CREATE] CurrentMedication dto={}", dto);

        Patient patient = getPatientOrThrow(dto.patientId());

        CurrentMedication entity = CurrentMedication.builder()
                .patient(patient)
                .activeIngredientId(dto.activeIngredientId())
                .instructions(dto.instructions())
                .startDate(dto.startDate())
                .build();

        try {
            CurrentMedication saved = currentMedicationRepository.saveAndFlush(entity);
            LOG.info("[CREATE] CurrentMedication created id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating current medication.",
                    "currentMedication",
                    "db.constraint"
            );
        }
    }

    public CurrentMedication update(CurrentMedicationUpdateDTO dto) {
        LOG.info("[UPDATE] CurrentMedication dto={}", dto);

        CurrentMedication entity = currentMedicationRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Current medication not found with id " + dto.id(),
                        "currentMedication",
                        "notfound"
                ));

        Patient patient = getPatientOrThrow(dto.patientId());

        entity.setPatient(patient);
        entity.setActiveIngredientId(dto.activeIngredientId());
        entity.setInstructions(dto.instructions());
        entity.setStartDate(dto.startDate());

        try {
            CurrentMedication updated = currentMedicationRepository.saveAndFlush(entity);
            LOG.info("[UPDATE] CurrentMedication updated id={}", updated.getId());
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating current medication.",
                    "currentMedication",
                    "db.constraint"
            );
        }
    }

    public void delete(Long id) {
        LOG.info("[DELETE] CurrentMedication id={}", id);

        CurrentMedication entity = currentMedicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Current medication not found with id " + id,
                        "currentMedication",
                        "notfound"
                ));

        currentMedicationRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public Page<CurrentMedication> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] CurrentMedication patientId={} pageable={}", patientId, pageable);
        return currentMedicationRepository.findAllByPatientId(patientId, pageable);
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());
        String lower = message != null ? message.toLowerCase() : "";

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        if (lower.contains("fk_current_medication_active_ingredient")) {
            throw new BadRequestAlertException(
                    "Active ingredient not found.",
                    "currentMedication",
                    "activeIngredient.notfound"
            );
        }

        if (lower.contains("fk_current_medication_patient")) {
            throw new BadRequestAlertException(
                    "Patient not found.",
                    "currentMedication",
                    "patient.notfound"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving current medication.",
                "currentMedication",
                "db.constraint"
        );
    }
}