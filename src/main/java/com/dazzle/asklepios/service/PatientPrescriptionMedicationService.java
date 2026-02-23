package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionMedicationService {

    private final PatientPrescriptionMedicationRepository repo;
    private final PatientPrescriptionRepository headerRepo;

    public PatientPrescriptionMedication create(PrescriptionMedicationCreateDTO prescriptionMedicationCreateDTO) {
        PatientPrescription header = headerRepo.findById(prescriptionMedicationCreateDTO.prescriptionHeaderId)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + prescriptionMedicationCreateDTO.prescriptionHeaderId));

        validateChronicVsDuration(prescriptionMedicationCreateDTO.chronicMedication, prescriptionMedicationCreateDTO.duration);

        PatientPrescriptionMedication entity = PatientPrescriptionMedication.builder()
                .prescriptionHeader(header)
                .medicationsId(prescriptionMedicationCreateDTO.medicationsId)
                .instructionsType(required(prescriptionMedicationCreateDTO.instructionsType, "instructionsType"))
                .instructions(buildInstructions(prescriptionMedicationCreateDTO))
                .dose(prescriptionMedicationCreateDTO.dose)
                .doesUnit(prescriptionMedicationCreateDTO.doesUnit)
                .rout(prescriptionMedicationCreateDTO.rout)
                .frequency(prescriptionMedicationCreateDTO.frequency)
                .chronicMedication(prescriptionMedicationCreateDTO.chronicMedication)
                .duration(prescriptionMedicationCreateDTO.duration)
                .durationType(prescriptionMedicationCreateDTO.durationType)
                .maximumDose(prescriptionMedicationCreateDTO.maximumDose)
                .validUtil(prescriptionMedicationCreateDTO.validUtil)
                .allowedSubstitute(prescriptionMedicationCreateDTO.allowedSubstitute)
                .indicationManually(prescriptionMedicationCreateDTO.indicationManually)
                .indicationUse(prescriptionMedicationCreateDTO.indicationUse)
                .indicationIcd(prescriptionMedicationCreateDTO.indicationIcd)
                .parametersToMonitor(prescriptionMedicationCreateDTO.parametersToMonitor)
                .numberOfRefills(prescriptionMedicationCreateDTO.numberOfRefills)
                .refillValue(prescriptionMedicationCreateDTO.refillValue)
                .refillUnit(prescriptionMedicationCreateDTO.refillUnit)
                .notes(prescriptionMedicationCreateDTO.notes)
                .extraDocumentation(prescriptionMedicationCreateDTO.extraDocumentation)
                .status(PrescriptionStatus.DRAFT)
                .build();

        return toDto(repo.save(entity));
    }

    public PatientPrescriptionMedication update(Long id, PrescriptionMedicationUpdateDTO vm) {
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

    @Transactional(readOnly = true)
    public PatientPrescriptionMedication get(Long id) {
        return repo.findById(id).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionMedication> list(Long prescriptionHeaderId, Pageable pageable) {
        return repo.findByPrescriptionHeader_Id(prescriptionHeaderId, pageable).map(this::toDto);
    }

    @Transactional
    public PatientPrescriptionMedication cancel(Long id) {
        PatientPrescriptionMedication entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);

        return toDto(repo.save(entity));
    }

    private void validateChronicVsDuration(Boolean chronic, Long duration) {
        if (Boolean.TRUE.equals(chronic) && duration != null) {
            throw new BadRequestAlertException(
                    "Cannot set chronic medication and duration together.",
                    "patientPrescriptionMedication",
                    "validate"
            );
        }
    }

    private void validateRoa(String roa) {
        if (roa == null || roa.isBlank()) {
            throw new BadRequestAlertException(
                    "ROA (rout) is required.",
                    "patientPrescriptionMedication",
                    "validate"
            );
        }
    }

    private <T> T required(T value, String field) {
        if (value == null)
        throw new BadRequestAlertException(
                 (field + " is required."),
                "patientPrescriptionMedication",
                "validate"
        );
        return value;
    }

    private String buildInstructions(PrescriptionMedicationCreateDTO dto) {

        if (dto.instructionsType == PrescriptionInstructionsType.MANUAL_INSTRUCTIONS
                || dto.instructionsType == PrescriptionInstructionsType.PRE_DEFINED_INSTRUCTIONS) {
            return dto.instructions;
        }

        String dosePart = (dto.dose != null ? dto.dose : "") + (dto.doesUnit != null ? " " + dto.doesUnit : "");
        String freqPart = (dto.frequency != null ? dto.frequency : "");
        String durationPart =
                (dto.duration != null ? " for " + dto.duration : "") + (dto.durationType != null ? " " + dto.durationType : "");

        return (dosePart + " " + freqPart + durationPart).trim();
    }

    private PatientPrescriptionMedication toDto(PatientPrescriptionMedication e) {
        PatientPrescriptionMedication dto = new PatientPrescriptionMedication();
        dto.setId(e.getId());
        dto.setPrescriptionHeader(e.getPrescriptionHeader());
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
