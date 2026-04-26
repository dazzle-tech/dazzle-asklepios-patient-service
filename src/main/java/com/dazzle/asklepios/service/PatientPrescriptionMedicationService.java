package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionMedicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPrescriptionMedicationService.class);
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;

    public PatientPrescriptionMedication create(PrescriptionMedicationCreateDTO prescriptionMedicationCreateDTO) {
        LOG.debug("create a PrescriptionMedicationCreateDTO={}",prescriptionMedicationCreateDTO);

        PatientPrescription header = patientPrescriptionRepository.findById(prescriptionMedicationCreateDTO.prescriptionHeaderId)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + prescriptionMedicationCreateDTO.prescriptionHeaderId));

        validateChronicVsDuration(prescriptionMedicationCreateDTO.chronicMedication, prescriptionMedicationCreateDTO.duration);
//TODO: add validation for medicationsId, activeIngredientId from setup service
        PatientPrescriptionMedication entity = PatientPrescriptionMedication.builder()
                .prescriptionHeader(header)
                .medicationsId(prescriptionMedicationCreateDTO.medicationsId)
                .activeIngredientId(prescriptionMedicationCreateDTO.activeIngredientId)
                .instructionsType(required(prescriptionMedicationCreateDTO.instructionsType, "instructionsType"))
                .instructions(prescriptionMedicationCreateDTO.instructions)
                .dose(prescriptionMedicationCreateDTO.dose)
                .doesUnit(prescriptionMedicationCreateDTO.doesUnit)
                .rout(prescriptionMedicationCreateDTO.rout)
                .frequency(prescriptionMedicationCreateDTO.frequency)
                .chronicMedication(prescriptionMedicationCreateDTO.chronicMedication)
                .duration(prescriptionMedicationCreateDTO.duration)
                .durationType(prescriptionMedicationCreateDTO.durationType)
                .maximumDose(prescriptionMedicationCreateDTO.maximumDose)
                .allowedSubstitute(prescriptionMedicationCreateDTO.allowedSubstitute)
                .indicationManually(prescriptionMedicationCreateDTO.indicationManually)
                .indicationUse(prescriptionMedicationCreateDTO.indicationUse)
                .indicationIcd(prescriptionMedicationCreateDTO.indicationIcd)
                .parametersToMonitor(prescriptionMedicationCreateDTO.parametersToMonitor)
                .administrationInstructions(prescriptionMedicationCreateDTO.administrationInstructions)
                .numberOfRefills(prescriptionMedicationCreateDTO.numberOfRefills)
                .refillValue(prescriptionMedicationCreateDTO.refillValue)
                .refillUnit(prescriptionMedicationCreateDTO.refillUnit)
                .notes(prescriptionMedicationCreateDTO.notes)
                .extraDocumentation(prescriptionMedicationCreateDTO.extraDocumentation)
                .status(PrescriptionStatus.DRAFT)
                .build();

        return toDto(patientPrescriptionMedicationRepository.save(entity));
    }

    public PatientPrescriptionMedication update(Long id, PrescriptionMedicationUpdateDTO prescriptionMedicationUpdateDTO) {
        LOG.debug("update a PrescriptionMedicationCreateDTO={}",prescriptionMedicationUpdateDTO);

        PatientPrescriptionMedication entity = patientPrescriptionMedicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));

        if (prescriptionMedicationUpdateDTO.instructionsType != null) entity.setInstructionsType(prescriptionMedicationUpdateDTO.instructionsType);

        if (prescriptionMedicationUpdateDTO.instructions != null) entity.setInstructions(prescriptionMedicationUpdateDTO.instructions);

        if (prescriptionMedicationUpdateDTO.dose != null) entity.setDose(prescriptionMedicationUpdateDTO.dose);
        if (prescriptionMedicationUpdateDTO.doesUnit != null) entity.setDoesUnit(prescriptionMedicationUpdateDTO.doesUnit);

        if (prescriptionMedicationUpdateDTO.rout != null) {
            entity.setRout(prescriptionMedicationUpdateDTO.rout);
        }
        if (prescriptionMedicationUpdateDTO.frequency != null) entity.setFrequency(prescriptionMedicationUpdateDTO.frequency);

        if (prescriptionMedicationUpdateDTO.chronicMedication != null || prescriptionMedicationUpdateDTO.duration != null) {
            Boolean chronic = prescriptionMedicationUpdateDTO.chronicMedication != null ? prescriptionMedicationUpdateDTO.chronicMedication : entity.getChronicMedication();
            Long duration = prescriptionMedicationUpdateDTO.duration != null ? prescriptionMedicationUpdateDTO.duration : entity.getDuration();
            validateChronicVsDuration(chronic, duration);

            if (prescriptionMedicationUpdateDTO.chronicMedication != null) entity.setChronicMedication(prescriptionMedicationUpdateDTO.chronicMedication);
            if (prescriptionMedicationUpdateDTO.duration != null) entity.setDuration(prescriptionMedicationUpdateDTO.duration);
            if (prescriptionMedicationUpdateDTO.durationType != null) entity.setDurationType(prescriptionMedicationUpdateDTO.durationType);
        }

        if (prescriptionMedicationUpdateDTO.maximumDose != null) entity.setMaximumDose(prescriptionMedicationUpdateDTO.maximumDose);
        if (prescriptionMedicationUpdateDTO.allowedSubstitute != null) entity.setAllowedSubstitute(prescriptionMedicationUpdateDTO.allowedSubstitute);

        if (prescriptionMedicationUpdateDTO.indicationManually != null) entity.setIndicationManually(prescriptionMedicationUpdateDTO.indicationManually);
        if (prescriptionMedicationUpdateDTO.indicationUse != null) entity.setIndicationUse(prescriptionMedicationUpdateDTO.indicationUse);
        if (prescriptionMedicationUpdateDTO.indicationIcd != null) entity.setIndicationIcd(prescriptionMedicationUpdateDTO.indicationIcd);
        if (prescriptionMedicationUpdateDTO.parametersToMonitor != null) entity.setParametersToMonitor(prescriptionMedicationUpdateDTO.parametersToMonitor);
        if (prescriptionMedicationUpdateDTO.administrationInstructions != null) entity.setParametersToMonitor(prescriptionMedicationUpdateDTO.administrationInstructions);
        if (prescriptionMedicationUpdateDTO.numberOfRefills != null) entity.setNumberOfRefills(prescriptionMedicationUpdateDTO.numberOfRefills);
        if (prescriptionMedicationUpdateDTO.refillValue != null) entity.setRefillValue(prescriptionMedicationUpdateDTO.refillValue);
        if (prescriptionMedicationUpdateDTO.refillUnit != null) entity.setRefillUnit(prescriptionMedicationUpdateDTO.refillUnit);

        if (prescriptionMedicationUpdateDTO.notes != null) entity.setNotes(prescriptionMedicationUpdateDTO.notes);
        if (prescriptionMedicationUpdateDTO.extraDocumentation != null) entity.setExtraDocumentation(prescriptionMedicationUpdateDTO.extraDocumentation);

        return toDto(patientPrescriptionMedicationRepository.save(entity));
    }


    public Page<PatientPrescriptionMedication> listAllChronicForPatient(Long patientId,  Pageable pageable) {
        LOG.debug("Fetching all listChronicForPatient for patientId={} ", patientId);

        return patientPrescriptionMedicationRepository.findByChronicMedicationTrueAndPrescriptionHeader_Patient_IdOrderByCreatedDateDesc(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public PatientPrescriptionMedication get(Long id) {
        LOG.debug("Fetching PatientPrescriptionMedication for id={}",id);

        return patientPrescriptionMedicationRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionMedication> list(Long prescriptionHeaderId, Pageable pageable) {
        LOG.debug("Fetching all PatientPrescriptionMedication for prescriptionHeaderId={}",prescriptionHeaderId);

        return patientPrescriptionMedicationRepository.findByPrescriptionHeader_Id(prescriptionHeaderId, pageable).map(this::toDto);
    }

    @Transactional
    public PatientPrescriptionMedication cancel(Long id) {
        LOG.debug("cancel PatientPrescriptionMedication for id={}",id);

        PatientPrescriptionMedication entity = patientPrescriptionMedicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);

        return toDto(patientPrescriptionMedicationRepository.save(entity));
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

    private <T> T required(T value, String field) {
        if (value == null)
            throw new BadRequestAlertException(
                    (field + " is required."),
                    "patientPrescriptionMedication",
                    "validate"
            );
        return value;
    }

    private PatientPrescriptionMedication toDto(PatientPrescriptionMedication e) {
        PatientPrescriptionMedication dto = new PatientPrescriptionMedication();
        dto.setId(e.getId());
        dto.setPrescriptionHeader(e.getPrescriptionHeader());
        dto.setMedicationsId(e.getMedicationsId());
        dto.setActiveIngredientId(e.getActiveIngredientId());
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