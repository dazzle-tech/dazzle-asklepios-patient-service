package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.service.vm.PrescriptionMedicationCreateVM;
import com.dazzle.asklepios.service.vm.PrescriptionMedicationUpdateVM;
import com.dazzle.asklepios.web.rest.dto.PatientPrescriptionMedicationDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionMedicationService {

    private final PatientPrescriptionMedicationRepository repo;
    private final PatientPrescriptionRepository headerRepo;

    // ===== create (VM -> Entity -> DTO) =====
    public PatientPrescriptionMedicationDTO create(PrescriptionMedicationCreateVM vm) {
        PatientPrescription header = headerRepo.findById(vm.prescriptionHeaderId)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + vm.prescriptionHeaderId));

        validateChronicVsDuration(vm.chronicMedication, vm.duration);
        validateRoa(vm.rout);

        PatientPrescriptionMedication entity = PatientPrescriptionMedication.builder()
                .prescriptionHeader(header)
                .medicationsId(vm.medicationsId)
                .instructionsType(required(vm.instructionsType, "instructionsType"))
                .instructions(buildInstructions(vm))
                .dose(vm.dose)
                .doesUnit(vm.doesUnit)
                .rout(vm.rout)
                .frequency(vm.frequency)
                .chronicMedication(vm.chronicMedication)
                .duration(vm.duration)
                .durationType(vm.durationType)
                .maximumDose(vm.maximumDose)
                .validUtil(vm.validUtil)
                .allowedSubstitute(vm.allowedSubstitute)
                .indicationManually(vm.indicationManually)
                .indicationUse(vm.indicationUse)
                .indicationIcd(vm.indicationIcd)
                .parametersToMonitor(vm.parametersToMonitor)
                .numberOfRefills(vm.numberOfRefills)
                .refillValue(vm.refillValue)
                .refillUnit(vm.refillUnit)
                .notes(vm.notes)
                .extraDocumentation(vm.extraDocumentation)
                .status(PrescriptionStatus.DRAFT)
                .build();

        return toDto(repo.save(entity));
    }

    // ===== update (VM -> Entity -> DTO) =====
    public PatientPrescriptionMedicationDTO update(Long id, PrescriptionMedicationUpdateVM vm) {
        PatientPrescriptionMedication entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));

        if (vm.instructionsType != null) entity.setInstructionsType(vm.instructionsType);

        if (vm.instructions != null) entity.setInstructions(vm.instructions);

        if (vm.dose != null) entity.setDose(vm.dose);
        if (vm.doesUnit != null) entity.setDoesUnit(vm.doesUnit);

        if (vm.rout != null) {
            validateRoa(vm.rout);
            entity.setRout(vm.rout);
        }
        if (vm.frequency != null) entity.setFrequency(vm.frequency);

        // XOR validation on updates
        if (vm.chronicMedication != null || vm.duration != null) {
            Boolean chronic = vm.chronicMedication != null ? vm.chronicMedication : entity.getChronicMedication();
            Long duration = vm.duration != null ? vm.duration : entity.getDuration();
            validateChronicVsDuration(chronic, duration);

            if (vm.chronicMedication != null) entity.setChronicMedication(vm.chronicMedication);
            if (vm.duration != null) entity.setDuration(vm.duration);
            if (vm.durationType != null) entity.setDurationType(vm.durationType);
        }

        if (vm.maximumDose != null) entity.setMaximumDose(vm.maximumDose);
        if (vm.validUtil != null) entity.setValidUtil(vm.validUtil);
        if (vm.allowedSubstitute != null) entity.setAllowedSubstitute(vm.allowedSubstitute);

        if (vm.indicationManually != null) entity.setIndicationManually(vm.indicationManually);
        if (vm.indicationUse != null) entity.setIndicationUse(vm.indicationUse);
        if (vm.indicationIcd != null) entity.setIndicationIcd(vm.indicationIcd);
        if (vm.parametersToMonitor != null) entity.setParametersToMonitor(vm.parametersToMonitor);

        if (vm.numberOfRefills != null) entity.setNumberOfRefills(vm.numberOfRefills);
        if (vm.refillValue != null) entity.setRefillValue(vm.refillValue);
        if (vm.refillUnit != null) entity.setRefillUnit(vm.refillUnit);

        if (vm.notes != null) entity.setNotes(vm.notes);
        if (vm.extraDocumentation != null) entity.setExtraDocumentation(vm.extraDocumentation);

        return toDto(repo.save(entity));
    }

    // ===== get/list (DTO) =====
    @Transactional(readOnly = true)
    public PatientPrescriptionMedicationDTO get(Long id) {
        return repo.findById(id).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionMedicationDTO> list(Long prescriptionHeaderId, Pageable pageable) {
        return repo.findByPrescriptionHeader_Id(prescriptionHeaderId, pageable).map(this::toDto);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("PatientPrescriptionMedication not found: " + id);
        }
        repo.deleteById(id);
    }

    // ===== validations =====
    private void validateChronicVsDuration(Boolean chronic, Long duration) {
        if (Boolean.TRUE.equals(chronic) && duration != null) {
            throw new IllegalArgumentException("Cannot set chronic medication and duration together.");
        }
    }

    private void validateRoa(String roa) {
        if (roa == null || roa.isBlank()) {
            throw new IllegalArgumentException("ROA (rout) is required.");
        }
    }

    private <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        return value;
    }

    // ===== instructions builder =====
    private String buildInstructions(PrescriptionMedicationCreateVM vm) {

        if (vm.instructionsType == PrescriptionInstructionsType.MANUAL_INSTRUCTIONS ||
                vm.instructionsType == PrescriptionInstructionsType.PRE_DEFINED_INSTRUCTIONS) {
            return vm.instructions; // الفرونت يرسل النص النهائي
        }

        // CUSTOM: إذا الفرونت ما يبنيها، الباك يبنيها
        String dosePart = (vm.dose != null ? vm.dose : "") + (vm.doesUnit != null ? " " + vm.doesUnit : "");
        String freqPart = (vm.frequency != null ? vm.frequency : "");
        String durationPart =
                (vm.duration != null ? " for " + vm.duration : "") + (vm.durationType != null ? " " + vm.durationType : "");

        return (dosePart + " " + freqPart + durationPart).trim();
    }

    // ===== mapper Entity -> DTO =====
    private PatientPrescriptionMedicationDTO toDto(PatientPrescriptionMedication e) {
        PatientPrescriptionMedicationDTO dto = new PatientPrescriptionMedicationDTO();
        dto.setId(e.getId());
        dto.setPrescriptionHeaderId(e.getPrescriptionHeader().getId());
        dto.setMedicationsId(e.getMedicationsId());
        dto.setInstructionsType(e.getInstructionsType());
        dto.setInstructions(e.getInstructions());
        dto.setDose(e.getDose());
        dto.setDoesUnit(e.getDoesUnit());
        dto.setRout(e.getRout());
        dto.setFrequency(e.getFrequency());
        dto.setDuration(e.getDuration());
        dto.setDurationType(e.getDurationType());
        dto.setChronicMedication(e.getChronicMedication());
        dto.setMaximumDose(e.getMaximumDose());
        dto.setValidUtil(e.getValidUtil());
        dto.setAllowedSubstitute(e.getAllowedSubstitute());
        dto.setIndicationManually(e.getIndicationManually());
        dto.setIndicationUse(e.getIndicationUse());
        dto.setIndicationIcd(e.getIndicationIcd());
        dto.setParametersToMonitor(e.getParametersToMonitor());
        dto.setNumberOfRefills(e.getNumberOfRefills());
        dto.setRefillValue(e.getRefillValue());
        dto.setRefillUnit(e.getRefillUnit());
        dto.setNotes(e.getNotes());
        dto.setExtraDocumentation(e.getExtraDocumentation());
        dto.setStatus(e.getStatus());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedDate(e.getCreatedDate());
        dto.setLastModifiedBy(e.getLastModifiedBy());
        dto.setLastModifiedDate(e.getLastModifiedDate());
        return dto;
    }
}
