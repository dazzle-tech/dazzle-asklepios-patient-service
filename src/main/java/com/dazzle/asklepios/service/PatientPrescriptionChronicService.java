package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPrescriptionChronic;
import com.dazzle.asklepios.repository.PatientPrescriptionChronicRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionChronicCreateDTO;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionChronicUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionChronicService {

    private final PatientPrescriptionChronicRepository patientPrescriptionChronicRepository;
    private final PatientRepository patientRepo;

    @Transactional
    public PatientPrescriptionChronic create(PatientPrescriptionChronicCreateDTO patientPrescriptionChronicCreateDTO) {
        Patient patient = patientRepo.findById(required(patientPrescriptionChronicCreateDTO.patientId, "patientId"))
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + patientPrescriptionChronicCreateDTO.patientId));

        boolean isActive = patientPrescriptionChronicCreateDTO.isActive == null || Boolean.TRUE.equals(patientPrescriptionChronicCreateDTO.isActive);

        if (isActive) {
            deactivateExistingActiveIfAny(
                    patient.getId(),
                    required(patientPrescriptionChronicCreateDTO.medicationsId, "medicationsId"),
                    required(patientPrescriptionChronicCreateDTO.activeIngredientId, "activeIngredientId"),
                    patientPrescriptionChronicCreateDTO.strength
            );
        }

        PatientPrescriptionChronic entity = PatientPrescriptionChronic.builder()
                .patient(patient)
                .encounterId(required(patientPrescriptionChronicCreateDTO.encounterId, "encounterId"))
                .medicationsId(patientPrescriptionChronicCreateDTO.medicationsId)
                .activeIngredientId(patientPrescriptionChronicCreateDTO.activeIngredientId)
                .strength(patientPrescriptionChronicCreateDTO.strength)
                .isActive(isActive)
                .build();

        return toDto(patientPrescriptionChronicRepository.save(entity));
    }

    public PatientPrescriptionChronic update(Long id, PatientPrescriptionChronicUpdateDTO patientPrescriptionChronicUpdateDTO) {
        PatientPrescriptionChronic entity = patientPrescriptionChronicRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionChronic not found: " + id));

        // Determine the final values after update (used for deactivation logic)
        Long newEncounterId = patientPrescriptionChronicUpdateDTO.encounterId != null ? patientPrescriptionChronicUpdateDTO.encounterId : entity.getEncounterId();
        Long newMedicationsId = patientPrescriptionChronicUpdateDTO.medicationsId != null ? patientPrescriptionChronicUpdateDTO.medicationsId : entity.getMedicationsId();
        Long newActiveIngredientId = patientPrescriptionChronicUpdateDTO.activeIngredientId != null ? patientPrescriptionChronicUpdateDTO.activeIngredientId : entity.getActiveIngredientId();
        var newStrength = patientPrescriptionChronicUpdateDTO.strength != null ? patientPrescriptionChronicUpdateDTO.strength : entity.getStrength();
        Boolean newIsActive = patientPrescriptionChronicUpdateDTO.isActive != null ? patientPrescriptionChronicUpdateDTO.isActive : entity.getIsActive();

        // If switching to active OR staying active but changing key fields => deactivate other active rows for the NEW combination
        boolean willBeActive = Boolean.TRUE.equals(newIsActive);

        boolean keyChanged =
                !Objects.equals(entity.getMedicationsId(), newMedicationsId) ||
                        !Objects.equals(entity.getActiveIngredientId(), newActiveIngredientId) ||
                        !Objects.equals(entity.getStrength(), newStrength);

        if (willBeActive && (keyChanged || !Boolean.TRUE.equals(entity.getIsActive()))) {
            deactivateExistingActiveIfAny(
                    entity.getPatient().getId(),
                    newMedicationsId,
                    newActiveIngredientId,
                    newStrength
            );
        }

        if (patientPrescriptionChronicUpdateDTO.encounterId != null) entity.setEncounterId(patientPrescriptionChronicUpdateDTO.encounterId);
        if (patientPrescriptionChronicUpdateDTO.medicationsId != null) entity.setMedicationsId(patientPrescriptionChronicUpdateDTO.medicationsId);
        if (patientPrescriptionChronicUpdateDTO.activeIngredientId != null) entity.setActiveIngredientId(patientPrescriptionChronicUpdateDTO.activeIngredientId);
        if (patientPrescriptionChronicUpdateDTO.strength != null) entity.setStrength(patientPrescriptionChronicUpdateDTO.strength);
        if (patientPrescriptionChronicUpdateDTO.isActive != null) entity.setIsActive(patientPrescriptionChronicUpdateDTO.isActive);

        try {
            return toDto(patientPrescriptionChronicRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestAlertException(
                    "There is already an active chronic record for the same patient/medication/ingredient/strength.",
                    "patientPrescriptionChronic",
                    "duplicateActive"
            );
        }
    }

    @Transactional(readOnly = true)
    public PatientPrescriptionChronic get(Long id) {
        return patientPrescriptionChronicRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionChronic not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionChronic> list(Long patientId, Pageable pageable) {
        return patientPrescriptionChronicRepository.findByPatient_Id(patientId, pageable).map(this::toDto);
    }

    public void delete(Long id) {
        if (!patientPrescriptionChronicRepository.existsById(id)) {
            throw new EntityNotFoundException("PatientPrescriptionChronic not found: " + id);
        }
        patientPrescriptionChronicRepository.deleteById(id);
    }

    private <T> T required(T value, String field) {
        if (value == null) {
            throw new BadRequestAlertException(
                    field + " is required.",
                    "patientPrescriptionChronic",
                    "validate"
            );
        }
        return value;
    }

    private PatientPrescriptionChronic toDto(PatientPrescriptionChronic e) {
        PatientPrescriptionChronic dto = new PatientPrescriptionChronic();
        dto.setId(e.getId());
        dto.setPatient(e.getPatient());
        dto.setEncounterId(e.getEncounterId());
        dto.setMedicationsId(e.getMedicationsId());
        dto.setActiveIngredientId(e.getActiveIngredientId());
        dto.setStrength(e.getStrength());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedDate(e.getCreatedDate());
        dto.setLastModifiedBy(e.getLastModifiedBy());
        dto.setLastModifiedDate(e.getLastModifiedDate());
        return dto;
    }

    private void deactivateExistingActiveIfAny(
            Long patientId,
            Long medicationsId,
            Long activeIngredientId,
            BigDecimal strength
    ) {
        Optional<PatientPrescriptionChronic> existingActive =
                (strength == null)
                        ? patientPrescriptionChronicRepository.findFirstByPatient_IdAndMedicationsIdAndActiveIngredientIdAndStrengthIsNullAndIsActiveTrue(
                        patientId, medicationsId, activeIngredientId
                )
                        : patientPrescriptionChronicRepository.findFirstByPatient_IdAndMedicationsIdAndActiveIngredientIdAndStrengthAndIsActiveTrue(
                        patientId, medicationsId, activeIngredientId, strength
                );

        existingActive.ifPresent(e -> {
            e.setIsActive(false);
            patientPrescriptionChronicRepository.save(e);
        });
    }




}
