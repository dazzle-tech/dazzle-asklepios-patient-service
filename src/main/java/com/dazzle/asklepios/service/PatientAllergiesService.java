package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.domain.PatientAllergiesActiveIngredient;
import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.repository.PatientAllergiesActiveIngredientsRepository;
import com.dazzle.asklepios.repository.PatientAllergiesRepository;
import com.dazzle.asklepios.service.dto.PatientAllergiesCreateDTO;
import com.dazzle.asklepios.service.dto.PatientAllergiesUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientAllergies.PatientAllergiesResponseVM;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;


@Service
@Slf4j
@Transactional
public class PatientAllergiesService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAllergiesService.class);
    private final PatientAllergiesRepository patientAllergiesRepository;
    private final PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository;

    public PatientAllergiesService(PatientAllergiesRepository patientAllergiesRepository, PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository) {
        this.patientAllergiesRepository = patientAllergiesRepository;
        this.patientAllergiesActiveIngredientRepository = patientAllergiesActiveIngredientRepository;
    }

    public PatientAllergies create(PatientAllergiesCreateDTO vm) {
        LOG.debug("Request to create Patient Allergies : {}", vm);
        if (vm.allergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The allergen type is medication : {}", vm);
            if (vm.medicationClassId() == null) {
                LOG.debug("The medication class id is null : {}", vm);
                throw new BadRequestAlertException(
                        "medicationClassIdRequired",
                        "patientAllergies",
                        "Medication Class ID is required for MEDICATION type"
                );
            }
            if (vm.allergenId() != null) {
                LOG.debug("The allergen id is not null : {}", vm);
                throw new BadRequestAlertException(
                        "allergenMustBeNull",
                        "patientAllergies",
                        "Allergen must be null for MEDICATION type"
                );
            }
        } else {
            if (vm.allergenId() == null) {
                LOG.debug("The allergen id is null : {}", vm);
                throw new BadRequestAlertException(
                        "allergenRequired",
                        "patientAllergies",
                        "Allergen ID is required for non-MEDICATION types"
                );
            }
            if (vm.medicationClassId() != null) {
                LOG.debug("The medication class id is not null : {}", vm);
                throw new BadRequestAlertException(
                        "medicationClassMustBeNull",
                        "patientAllergies",
                        "Medication Class must be null for non-MEDICATION types"
                );
            }
            if (vm.activeIngredients() != null && !vm.activeIngredients().isEmpty()) {
                LOG.debug("The active ingredients list is not empty : {}", vm);
                throw new BadRequestAlertException(
                        "activeIngredientsMustBeEmpty",
                        "patientAllergies",
                        "Active Ingredients must be empty for non-MEDICATION types"
                );
            }
        }
        if (vm.onsetDateUndefined() && vm.onsetDate() != null) {
            LOG.debug("The onset date is not null : {}", vm);
            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientAllergies",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!vm.onsetDateUndefined() && vm.onsetDate() == null) {
            LOG.debug("The onset date is null : {}", vm);
            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientAllergies",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        if (vm.byPatient() && vm.sourceOfInformation() != null) {
            LOG.debug("The source of information is not null : {}", vm);
            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientAllergies",
                    "source of Information must be null when by Patient is true"
            );
        }
        if (!vm.byPatient() && vm.sourceOfInformation() == null) {
            LOG.debug("The source of information is null : {}", vm);
            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientAllergies",
                    "source of Information is required when by Patient is false"
            );
        }
        PatientAllergies entity = PatientAllergies.builder()
                .patientId(vm.patientId())
                .encounterId(vm.encounterId())
                .allergenType(vm.allergenType())
                .allergenId(vm.allergenId())
                .severity(vm.severity())
                .medicationClassId(vm.medicationClassId())
                .criticality(vm.criticality())
                .certainty(vm.certainty())
                .treatmentStrategy(vm.treatmentStrategy())
                .onset(vm.onset())
                .onsetDateUndefined(vm.onsetDateUndefined())
                .onsetDate(vm.onsetDate())
                .typeOfPropensity(vm.typeOfPropensity())
                .byPatient(vm.byPatient())
                .sourceOfInformation(vm.sourceOfInformation())
                .note(vm.note())
                .allergicReactions(vm.allergicReactions())
                .status(vm.status())
                .resolvedBy(vm.resolvedBy())
                .resolvedDate(vm.resolvedDate())
                .cancelledBy(vm.cancelledBy())
                .cancelledDate(vm.cancelledDate())
                .cancellationReason(vm.cancellationReason())
                .build();

        try {
            PatientAllergies saved = patientAllergiesRepository.save(entity);
            if (vm.allergenType() == AllergenTypes.MEDICATION &&
                    vm.activeIngredients() != null &&
                    !vm.activeIngredients().isEmpty()) {
                LOG.debug("Save active ingredients");
                for (Long activeIngredient : vm.activeIngredients()) {
                    PatientAllergiesActiveIngredient ai = new PatientAllergiesActiveIngredient();
                    ai.setPatientAllergy(saved);
                    ai.setActiveIngredientId(activeIngredient);
                    patientAllergiesActiveIngredientRepository.save(ai);
                }
            }

            LOG.debug("Created PatientAllergies: {}", saved);
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw handleConstraintViolation(constraintException);

        }
    }


    @Transactional(readOnly = true)
    public Page<PatientAllergiesResponseVM> findAllAllergiesByPatientId(
            Pageable pageable,
            boolean showCancelled,
            Long patientId
    ) {
        Page<PatientAllergies> page;
        if (showCancelled) {
            LOG.debug("Fetch PatientAllergies with cancelled allergies");
            page = patientAllergiesRepository.findByPatientId(patientId, pageable);
        } else {
            LOG.debug("Fetch PatientAllergies without cancelled allergies");
            page = patientAllergiesRepository.findByPatientIdAndStatusNot(
                    patientId,
                    PatientAllergyStatus.CANCELLED,
                    pageable
            );
        }
        return page.map(allergy ->
                PatientAllergiesResponseVM.ofEntity(
                        allergy,
                        patientAllergiesActiveIngredientRepository
                )
        );
    }


    @Transactional
    public PatientAllergiesResponseVM cancel(Long id, String cancelledBy, String reason) {
        LOG.debug("Request to cancel PatientAllergy: {}", id);

        PatientAllergies entity = patientAllergiesRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientAllergies",
                        "PatientAllergies not found"
                ));

        entity.setStatus(PatientAllergyStatus.CANCELLED);
        entity.setCancelledBy(cancelledBy);
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(reason);

        return PatientAllergiesResponseVM.ofEntity(
                entity,
                patientAllergiesActiveIngredientRepository
        );
    }

    @Transactional
    public PatientAllergiesResponseVM resolve(Long id, String resolvedBy) {
        LOG.debug("Request to resolve PatientAllergy : {}", id);

        PatientAllergies entity = patientAllergiesRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientAllergies",
                        "PatientAllergies not found"
                ));

        entity.setStatus(PatientAllergyStatus.RESOLVED);
        entity.setResolvedBy(resolvedBy);
        entity.setResolvedDate(Instant.now());

        return PatientAllergiesResponseVM.ofEntity(
                entity,
                patientAllergiesActiveIngredientRepository
        );
    }

    @Transactional
    public PatientAllergiesResponseVM undoResolve(Long id) {
        LOG.debug("Request to undo resolve PatientAllergy : {}", id);

        PatientAllergies entity = patientAllergiesRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientAllergies",
                        "PatientAllergies not found"
                ));

        if (entity.getStatus() != PatientAllergyStatus.RESOLVED) {
            throw new BadRequestAlertException(
                    "PatientAllergies is not resolved",
                    "patientAllergies",
                    "notResolved"
            );
        }

        entity.setStatus(PatientAllergyStatus.ACTIVE);

        return PatientAllergiesResponseVM.ofEntity(
                entity,
                patientAllergiesActiveIngredientRepository
        );
    }


    @Transactional
    public PatientAllergies update(PatientAllergiesUpdateDTO dto) {
        LOG.debug("Request to update PatientAllergy: {}", dto);
        if (dto.allergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The updated allergen type is medication : {}", dto);
            if (dto.medicationClassId() == null) {
                LOG.debug("The updated medication class id is null : {}", dto);
                throw new BadRequestAlertException(
                        "medicationClassIdRequired",
                        "patientAllergies",
                        "Medication Class ID is required for MEDICATION type"
                );
            }
            if (dto.allergenId() != null) {
                LOG.debug("The updated allergen id is not null : {}", dto);
                throw new BadRequestAlertException(
                        "allergenMustBeNull",
                        "patientAllergies",
                        "Allergen must be null for MEDICATION type"
                );
            }
        } else {
            if (dto.allergenId() == null) {
                LOG.debug("The updated allergen id is null : {}", dto);
                throw new BadRequestAlertException(
                        "allergenRequired",
                        "patientAllergies",
                        "Allergen ID is required for non-MEDICATION types"
                );
            }
            if (dto.medicationClassId() != null) {
                LOG.debug("The updated medication class id is not null : {}", dto);
                throw new BadRequestAlertException(
                        "medicationClassMustBeNull",
                        "patientAllergies",
                        "Medication Class must be null for non-MEDICATION types"
                );
            }
            if (dto.activeIngredients() != null && !dto.activeIngredients().isEmpty()) {
                LOG.debug("The updated active ingredients list is not empty : {}", dto);
                throw new BadRequestAlertException(
                        "activeIngredientsMustBeEmpty",
                        "patientAllergies",
                        "Active Ingredients must be empty for non-MEDICATION types"
                );
            }
        }
        if (dto.onsetDateUndefined() && dto.onsetDate() != null) {
            LOG.debug("The updated onset date is not null : {}", dto);
            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientAllergies",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!dto.onsetDateUndefined() && dto.onsetDate() == null) {
            LOG.debug("The updated onset date is null : {}", dto);
            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientAllergies",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        if (dto.byPatient() && dto.sourceOfInformation() != null) {
            LOG.debug("The updated source of information is not null : {}", dto);
            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientAllergies",
                    "source of Information must be null when by Patient is true"
            );
        }
        if (!dto.byPatient() && dto.sourceOfInformation() == null) {
            LOG.debug("The updated source of information is null : {}", dto);
            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientAllergies",
                    "source of Information is required when by Patient is false"
            );
        }
        PatientAllergies entity = patientAllergiesRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientAllergies",
                        "PatientAllergies not found with id " + dto.id()
                ));

        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        if (entity.getCreatedDate().isBefore(todayStart)) {
            LOG.debug("The created date is before today: {}", entity.getCreatedDate());
            throw new BadRequestAlertException(
                    "updateNotAllowed",
                    "patientAllergies",
                    "Only today's records can be updated"
            );
        }

        entity.setAllergenType(dto.allergenType());
        entity.setAllergenId(dto.allergenId());
        entity.setSeverity(dto.severity());
        entity.setMedicationClassId(dto.medicationClassId());
        entity.setCriticality(dto.criticality());
        entity.setCertainty(dto.certainty());
        entity.setTreatmentStrategy(dto.treatmentStrategy());
        entity.setOnset(dto.onset());
        entity.setOnsetDateUndefined(dto.onsetDateUndefined() != null && dto.onsetDateUndefined());
        entity.setOnsetDate(dto.onsetDate());
        entity.setTypeOfPropensity(dto.typeOfPropensity());
        entity.setByPatient(dto.byPatient() != null && dto.byPatient());
        entity.setSourceOfInformation(dto.sourceOfInformation());
        entity.setNote(dto.note());
        entity.setAllergicReactions(dto.allergicReactions());

        if (entity.getAllergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The updated allergen type is medication : {}", dto);
            List<Long> newIds = dto.activeIngredients() == null
                    ? List.of()
                    : new ArrayList<>(dto.activeIngredients());

            List<PatientAllergiesActiveIngredient> current =
                    patientAllergiesActiveIngredientRepository.findByPatientAllergy(entity);

            current.stream()
                    .filter(ai -> !newIds.contains(ai.getActiveIngredientId()))
                    .forEach(patientAllergiesActiveIngredientRepository::delete);

            for (Long id : newIds) {
                boolean exists = current.stream()
                        .anyMatch(ai -> ai.getActiveIngredientId().equals(id));
                if (!exists) {
                    PatientAllergiesActiveIngredient ai = new PatientAllergiesActiveIngredient();
                    ai.setPatientAllergy(entity);
                    ai.setActiveIngredientId(id);
                    patientAllergiesActiveIngredientRepository.save(ai);
                }
            }

        } else {
            patientAllergiesActiveIngredientRepository.deleteByPatientAllergy(entity);
        }

        try {
            PatientAllergies updated = patientAllergiesRepository.saveAndFlush(entity);
            LOG.debug("Updated PatientAllergies: {}", updated);
            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            LOG.debug("Constraint violation caught during update");
            throw handleConstraintViolation(constraintException);
        }
    }


    private BadRequestAlertException handleConstraintViolation(RuntimeException constraintException) {
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation while saving patient allergy: {}", message, constraintException);

        if (msgLower.contains("uk_patient_allergies_allergen")
                || msgLower.contains("uk_patient_allergies_medication_class")) {

            return new BadRequestAlertException(
                    "unique.allergen",
                    "patient_allergies",
                    "You can`t add the same allergen for the same patient"
            );
        }

        if (msgLower.contains("fk_patient_allergies_patient_id")) {

            return new BadRequestAlertException(
                    "patientId",
                    "patient_allergies",
                    "The patient does not exist"
            );
        }

        return new BadRequestAlertException(
                "db.constraint",
                "patient_allergies",
                "Database constraint violated while saving patient allergy"
        );
    }



}
