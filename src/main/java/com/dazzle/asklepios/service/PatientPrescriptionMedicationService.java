package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ActiveIngredientClient;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationUpdateDTO;
import com.dazzle.asklepios.service.helper.BrandMedicationHelper;
import com.dazzle.asklepios.service.helper.ICDTreeHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionMedicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPrescriptionMedicationService.class);
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final ActiveIngredientClient activeIngredientClient;
    private final BrandMedicationHelper brandMedicationHelper;
    private final ICDTreeHelper icdTreeHelper;
    private final PatientServiceAndProductService patientServiceAndProductService;

    public PatientPrescriptionMedication create(PrescriptionMedicationCreateDTO prescriptionMedicationCreateDTO) {
        LOG.debug("create a PrescriptionMedicationCreateDTO={}", prescriptionMedicationCreateDTO);

        PatientPrescription header = patientPrescriptionRepository.findById(prescriptionMedicationCreateDTO.prescriptionHeaderId)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + prescriptionMedicationCreateDTO.prescriptionHeaderId));

        validateChronicVsDuration(prescriptionMedicationCreateDTO.chronicMedication, prescriptionMedicationCreateDTO.duration);
        validateMedicationSelection(
                prescriptionMedicationCreateDTO.activeIngredientId,
                prescriptionMedicationCreateDTO.medicationsId,
                prescriptionMedicationCreateDTO.otherMedicationName
        );
        PatientPrescriptionMedication entity = PatientPrescriptionMedication.builder()
                .prescriptionHeader(header)
                .medicationsId(
                        prescriptionMedicationCreateDTO.medicationsId
                )
                .otherMedicationName(
                        prescriptionMedicationCreateDTO.otherMedicationName != null
                                ? prescriptionMedicationCreateDTO.otherMedicationName.trim()
                                : null
                )
                .activeIngredientId(
                        prescriptionMedicationCreateDTO.activeIngredientId
                )
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

        return patientPrescriptionMedicationRepository.saveAndFlush(entity);
    }

    public PatientPrescriptionMedication update(Long id, PrescriptionMedicationUpdateDTO prescriptionMedicationUpdateDTO) {
        LOG.debug("update a PrescriptionMedicationCreateDTO={}", prescriptionMedicationUpdateDTO);

        PatientPrescriptionMedication entity = patientPrescriptionMedicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));
        if (prescriptionMedicationUpdateDTO.instructionsType != null)
            entity.setInstructionsType(prescriptionMedicationUpdateDTO.instructionsType);

        if (prescriptionMedicationUpdateDTO.instructions != null)
            entity.setInstructions(prescriptionMedicationUpdateDTO.instructions);

        if (prescriptionMedicationUpdateDTO.dose != null) entity.setDose(prescriptionMedicationUpdateDTO.dose);
        if (prescriptionMedicationUpdateDTO.doesUnit != null)
            entity.setDoesUnit(prescriptionMedicationUpdateDTO.doesUnit);

        if (prescriptionMedicationUpdateDTO.rout != null) {
            entity.setRout(prescriptionMedicationUpdateDTO.rout);
        }
        if (prescriptionMedicationUpdateDTO.frequency != null)
            entity.setFrequency(prescriptionMedicationUpdateDTO.frequency);

        if (prescriptionMedicationUpdateDTO.chronicMedication != null || prescriptionMedicationUpdateDTO.duration != null) {
            Boolean chronic = prescriptionMedicationUpdateDTO.chronicMedication != null ? prescriptionMedicationUpdateDTO.chronicMedication : entity.getChronicMedication();
            Long duration = prescriptionMedicationUpdateDTO.duration != null ? prescriptionMedicationUpdateDTO.duration : entity.getDuration();
            validateChronicVsDuration(chronic, duration);

            if (prescriptionMedicationUpdateDTO.chronicMedication != null)
                entity.setChronicMedication(prescriptionMedicationUpdateDTO.chronicMedication);
            if (prescriptionMedicationUpdateDTO.duration != null)
                entity.setDuration(prescriptionMedicationUpdateDTO.duration);
            if (prescriptionMedicationUpdateDTO.durationType != null)
                entity.setDurationType(prescriptionMedicationUpdateDTO.durationType);
        }

        if (prescriptionMedicationUpdateDTO.maximumDose != null)
            entity.setMaximumDose(prescriptionMedicationUpdateDTO.maximumDose);
        if (prescriptionMedicationUpdateDTO.allowedSubstitute != null)
            entity.setAllowedSubstitute(prescriptionMedicationUpdateDTO.allowedSubstitute);

        if (prescriptionMedicationUpdateDTO.indicationManually != null)
            entity.setIndicationManually(prescriptionMedicationUpdateDTO.indicationManually);
        if (prescriptionMedicationUpdateDTO.indicationUse != null)
            entity.setIndicationUse(prescriptionMedicationUpdateDTO.indicationUse);
        if (prescriptionMedicationUpdateDTO.indicationIcd != null) {
            icdTreeHelper.validateICDDiagnosisExists(prescriptionMedicationUpdateDTO.indicationIcd);
            entity.setIndicationIcd(prescriptionMedicationUpdateDTO.indicationIcd);
        }
        if (prescriptionMedicationUpdateDTO.parametersToMonitor != null)
            entity.setParametersToMonitor(prescriptionMedicationUpdateDTO.parametersToMonitor);
        if (prescriptionMedicationUpdateDTO.administrationInstructions != null)
            entity.setAdministrationInstructions(
                    prescriptionMedicationUpdateDTO.administrationInstructions
            );
        if (prescriptionMedicationUpdateDTO.numberOfRefills != null)
            entity.setNumberOfRefills(prescriptionMedicationUpdateDTO.numberOfRefills);
        if (prescriptionMedicationUpdateDTO.refillValue != null)
            entity.setRefillValue(prescriptionMedicationUpdateDTO.refillValue);
        if (prescriptionMedicationUpdateDTO.refillUnit != null)
            entity.setRefillUnit(prescriptionMedicationUpdateDTO.refillUnit);

        if (prescriptionMedicationUpdateDTO.notes != null) entity.setNotes(prescriptionMedicationUpdateDTO.notes);
        if (prescriptionMedicationUpdateDTO.extraDocumentation != null)
            entity.setExtraDocumentation(prescriptionMedicationUpdateDTO.extraDocumentation);
        if (prescriptionMedicationUpdateDTO.medicationsId != null) {
            brandMedicationHelper.validateBrandMedicationExists(
                    prescriptionMedicationUpdateDTO.medicationsId
            );
            entity.setMedicationsId(
                    prescriptionMedicationUpdateDTO.medicationsId
            );
        }

        if (prescriptionMedicationUpdateDTO.activeIngredientId != null) {
            activeIngredientClient.existsActiveIngredient(
                    prescriptionMedicationUpdateDTO.activeIngredientId
            );
            entity.setActiveIngredientId(
                    prescriptionMedicationUpdateDTO.activeIngredientId
            );
        }

        if (prescriptionMedicationUpdateDTO.otherMedicationName != null) {
            String otherMedicationName =
                    prescriptionMedicationUpdateDTO.otherMedicationName.trim();

            if (otherMedicationName.isEmpty()) {
                throw new BadRequestAlertException(
                        "Active Ingredient or Other Medication Name is required.",
                        "patientPrescriptionMedication",
                        "medicationRequired"
                );
            }

            entity.setOtherMedicationName(otherMedicationName);
        }

        return patientPrescriptionMedicationRepository.saveAndFlush(entity);
    }


    public Page<PatientPrescriptionMedication> listAllChronicForPatient(Long patientId, Pageable pageable) {
        LOG.debug("Fetching all listChronicForPatient for patientId={} ", patientId);

        return patientPrescriptionMedicationRepository.findByChronicMedicationTrueAndPrescriptionHeader_Patient_IdOrderByCreatedDateDesc(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public PatientPrescriptionMedication get(Long id) {
        LOG.debug("Fetching PatientPrescriptionMedication for id={}", id);

        return patientPrescriptionMedicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionMedication> list(Long prescriptionHeaderId, Pageable pageable) {
        LOG.debug("Fetching all PatientPrescriptionMedication for prescriptionHeaderId={}", prescriptionHeaderId);

        return patientPrescriptionMedicationRepository.findByPrescriptionHeader_Id(prescriptionHeaderId, pageable);
    }

    @Transactional
    public PatientPrescriptionMedication cancel(Long id ,String cancellationReason) {
        LOG.debug("cancel PatientPrescriptionMedication for id={}", id);

        PatientPrescriptionMedication entity = patientPrescriptionMedicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescriptionMedication not found: " + id));

        patientServiceAndProductService.cancelBySource(
                ServiceSource.PRESCRIPTION,
                entity.getId(),
                BillingItemTypes.MEDICATION,
                "Prescription medication cancelled"
        );

        entity.setStatus(PrescriptionStatus.CANCELLED);
        entity.setCancellationReason(cancellationReason);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        return patientPrescriptionMedicationRepository.saveAndFlush(entity);
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

    private void validateMedicationSelection(
            Long activeIngredientId,
            Long medicationsId,
            String otherMedicationName
    ) {
        if (activeIngredientId == null) {
            throw new BadRequestAlertException(
                    "Active Ingredient is required.",
                    "patientPrescriptionMedication",
                    "activeIngredientRequired"
            );
        }

        activeIngredientClient.existsActiveIngredient(activeIngredientId);

        if (medicationsId != null) {
            brandMedicationHelper.validateBrandMedicationExists(medicationsId);
        }

        if (otherMedicationName != null && otherMedicationName.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Other Medication Name cannot be empty.",
                    "patientPrescriptionMedication",
                    "otherMedicationInvalid"
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
    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("No authenticated user", "Prescription-medication", "unauthenticated"));
    }
}