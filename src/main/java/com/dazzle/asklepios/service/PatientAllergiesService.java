package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.domain.PatientAllergiesActiveIngredient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.repository.PatientAllergiesActiveIngredientsRepository;
import com.dazzle.asklepios.repository.PatientAllergiesRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.PatientAllergiesCreateDTO;
import com.dazzle.asklepios.service.dto.PatientAllergiesUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientAllergies.PatientAllergiesResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;


@Service
@Transactional
public class PatientAllergiesService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAllergiesService.class);
    private final PatientAllergiesRepository patientAllergiesRepository;
    private final PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public PatientAllergiesService(PatientAllergiesRepository patientAllergiesRepository, PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository, PatientRepository patientRepository, PatientEncounterRepository patientEncounterRepository) {
        this.patientAllergiesRepository = patientAllergiesRepository;
        this.patientAllergiesActiveIngredientRepository = patientAllergiesActiveIngredientRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public PatientAllergies create(PatientAllergiesCreateDTO patientAllergyCreateDto) {
        LOG.debug("Request to create Patient Allergies : {}", patientAllergyCreateDto);
        if (patientAllergyCreateDto.allergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The allergen type is medication : {}", patientAllergyCreateDto);
            if (patientAllergyCreateDto.medicationClassId() == null) {
                LOG.debug("The medication class id is null : {}", patientAllergyCreateDto);
                throw new BadRequestAlertException(
                        "medicationClassIdRequired",
                        "patientAllergies",
                        "Medication Class ID is required for MEDICATION type"
                );
            }
            if (patientAllergyCreateDto.allergenId() != null) {
                LOG.debug("The allergen id is not null : {}", patientAllergyCreateDto);
                throw new BadRequestAlertException(
                        "allergenMustBeNull",
                        "patientAllergies",
                        "Allergen must be null for MEDICATION type"
                );
            }
        } else {
            if (patientAllergyCreateDto.allergenId() == null) {
                LOG.debug("The allergen id is null : {}", patientAllergyCreateDto);
                throw new BadRequestAlertException(
                        "allergenRequired",
                        "patientAllergies",
                        "Allergen ID is required for non-MEDICATION types"
                );
            }
            if (patientAllergyCreateDto.medicationClassId() != null) {
                LOG.debug("The medication class id is not null : {}", patientAllergyCreateDto);
                throw new BadRequestAlertException(
                        "medicationClassMustBeNull",
                        "patientAllergies",
                        "Medication Class must be null for non-MEDICATION types"
                );
            }
            if (patientAllergyCreateDto.activeIngredients() != null && !patientAllergyCreateDto.activeIngredients().isEmpty()) {
                LOG.debug("The active ingredients list is not empty : {}", patientAllergyCreateDto);
                throw new BadRequestAlertException(
                        "activeIngredientsMustBeEmpty",
                        "patientAllergies",
                        "Active Ingredients must be empty for non-MEDICATION types"
                );
            }
        }
        if (patientAllergyCreateDto.onsetDateUndefined() && patientAllergyCreateDto.onsetDate() != null) {
            LOG.debug("The onset date is not null : {}", patientAllergyCreateDto);
            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientAllergies",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!patientAllergyCreateDto.onsetDateUndefined() && patientAllergyCreateDto.onsetDate() == null) {
            LOG.debug("The onset date is null : {}", patientAllergyCreateDto);
            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientAllergies",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        // Validate onset date is not in the future
        if (patientAllergyCreateDto.onsetDate() != null &&
                patientAllergyCreateDto.onsetDate().isAfter(Instant.now())) {

            LOG.debug("The onset date is in the future : {}", patientAllergyCreateDto.onsetDate());

            throw new BadRequestAlertException(
                    "onsetDateInFuture",
                    "patientAllergies",
                    "Onset Date cannot be in the future"
            );
        }

        if (patientAllergyCreateDto.byPatient() && patientAllergyCreateDto.sourceOfInformation() != null) {
            LOG.debug("The source of information is not null : {}", patientAllergyCreateDto);
            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientAllergies",
                    "source of Information must be null"
            );
        }
        if (!patientAllergyCreateDto.byPatient() && patientAllergyCreateDto.sourceOfInformation() == null) {
            LOG.debug("The source of information is null : {}", patientAllergyCreateDto);
            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientAllergies",
                    "source of Information is required"
            );
        }
        Patient patient = patientRepository.findById(patientAllergyCreateDto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientAllergyCreateDto.patientId(),
                        "painAssessment",
                        "patient.notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(patientAllergyCreateDto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + patientAllergyCreateDto.encounterId(),
                        "painAssessment",
                        "encounter.notfound"
                ));
        //TODO: allergen id, medicationClassId validation from setup service
        PatientAllergies entity = PatientAllergies.builder()
                .patientId(patient.getId())
                .encounterId(encounter.getId())
                .allergenType(patientAllergyCreateDto.allergenType())
                .allergenId(patientAllergyCreateDto.allergenId())
                .severity(patientAllergyCreateDto.severity())
                .medicationClassId(patientAllergyCreateDto.medicationClassId())
                .criticality(patientAllergyCreateDto.criticality())
                .certainty(patientAllergyCreateDto.certainty())
                .treatmentStrategy(patientAllergyCreateDto.treatmentStrategy())
                .onset(patientAllergyCreateDto.onset())
                .onsetDateUndefined(patientAllergyCreateDto.onsetDateUndefined())
                .onsetDate(patientAllergyCreateDto.onsetDate())
                .typeOfPropensity(patientAllergyCreateDto.typeOfPropensity())
                .byPatient(patientAllergyCreateDto.byPatient())
                .sourceOfInformation(patientAllergyCreateDto.sourceOfInformation())
                .note(patientAllergyCreateDto.note())
                .allergicReactions(patientAllergyCreateDto.allergicReactions())
                .status(patientAllergyCreateDto.status())
                .build();

        try {
            PatientAllergies saved = patientAllergiesRepository.save(entity);
            if (patientAllergyCreateDto.allergenType() == AllergenTypes.MEDICATION &&
                    patientAllergyCreateDto.activeIngredients() != null &&
                    !patientAllergyCreateDto.activeIngredients().isEmpty()) {
                LOG.debug("Save active ingredients");
                for (Long activeIngredient : patientAllergyCreateDto.activeIngredients()) {
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
    public PatientAllergiesResponseVM cancel(Long id, String reason) {
        LOG.debug("Request to cancel PatientAllergy: {}", id);
        String login = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));


        PatientAllergies entity = patientAllergiesRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientAllergies",
                        "PatientAllergies not found"
                ));

        entity.setStatus(PatientAllergyStatus.CANCELLED);
        entity.setCancelledBy(login);
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(reason);

        return PatientAllergiesResponseVM.ofEntity(
                entity,
                patientAllergiesActiveIngredientRepository
        );
    }

    @Transactional
    public PatientAllergiesResponseVM resolve(Long id) {
        LOG.debug("Request to resolve PatientAllergy : {}", id);

        PatientAllergies entity = patientAllergiesRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientAllergies",
                        "PatientAllergies not found"
                ));

        String login = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        entity.setStatus(PatientAllergyStatus.RESOLVED);
        entity.setResolvedBy(login);
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
    public PatientAllergies update(PatientAllergiesUpdateDTO patientAllergiesUpdateDTO) {
        LOG.debug("Request to update PatientAllergy: {}", patientAllergiesUpdateDTO);
        PatientAllergies entity = patientAllergiesRepository.findById(patientAllergiesUpdateDTO.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientAllergies",
                        "PatientAllergies not found with id " + patientAllergiesUpdateDTO.id()
                ));
        // Only active warnings can be updated
        if (!(entity.getStatus() == PatientAllergyStatus.ACTIVE)) {
            LOG.debug("The updated warning status is not active : {}", entity.getStatus());
            throw new BadRequestAlertException(
                    "statusMustBeActive",
                    "patientWarnings",
                    "Status must be Active"
            );
        }
        if (patientAllergiesUpdateDTO.allergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The updated allergen type is medication : {}", patientAllergiesUpdateDTO);
            if (patientAllergiesUpdateDTO.medicationClassId() == null) {
                LOG.debug("The updated medication class id is null : {}", patientAllergiesUpdateDTO);
                throw new BadRequestAlertException(
                        "medicationClassIdRequired",
                        "patientAllergies",
                        "Medication Class ID is required for MEDICATION type"
                );
            }
            if (patientAllergiesUpdateDTO.allergenId() != null) {
                LOG.debug("The updated allergen id is not null : {}", patientAllergiesUpdateDTO);

                throw new BadRequestAlertException(
                        "allergenMustBeNull",
                        "patientAllergies",
                        "Allergen must be null for MEDICATION type"
                );
            }
        } else {
            if (patientAllergiesUpdateDTO.allergenId() == null) {
                LOG.debug("The updated allergen id is null : {}", patientAllergiesUpdateDTO);

                throw new BadRequestAlertException(
                        "allergenRequired",
                        "patientAllergies",
                        "Allergen ID is required for non-MEDICATION types"
                );
            }
            if (patientAllergiesUpdateDTO.medicationClassId() != null) {
                LOG.debug("The updated medication class id is not null : {}", patientAllergiesUpdateDTO);

                throw new BadRequestAlertException(
                        "medicationClassMustBeNull",
                        "patientAllergies",
                        "Medication Class must be null for non-MEDICATION types"
                );
            }
            if (patientAllergiesUpdateDTO.activeIngredients() != null && !patientAllergiesUpdateDTO.activeIngredients().isEmpty()) {
                LOG.debug("The updated active ingredients list is not empty : {}", patientAllergiesUpdateDTO);

                throw new BadRequestAlertException(
                        "activeIngredientsMustBeEmpty",
                        "patientAllergies",
                        "Active Ingredients must be empty for non-MEDICATION types"
                );
            }
        }
        if (patientAllergiesUpdateDTO.onsetDateUndefined() && patientAllergiesUpdateDTO.onsetDate() != null) {
            LOG.debug("The updated onset date is not null : {}", patientAllergiesUpdateDTO);

            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientAllergies",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!patientAllergiesUpdateDTO.onsetDateUndefined() && patientAllergiesUpdateDTO.onsetDate() == null) {
            LOG.debug("The updated onset date is null : {}", patientAllergiesUpdateDTO);

            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientAllergies",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        // Validate onset date is not in the future
        if (patientAllergiesUpdateDTO.onsetDate() != null &&
                patientAllergiesUpdateDTO.onsetDate().isAfter(Instant.now())) {

            LOG.debug("The updated onset date is in the future : {}", patientAllergiesUpdateDTO.onsetDate());

            throw new BadRequestAlertException(
                    "onsetDateInFuture",
                    "patientAllergies",
                    "Onset Date cannot be in the future"
            );
        }
        if (patientAllergiesUpdateDTO.byPatient() && patientAllergiesUpdateDTO.sourceOfInformation() != null) {
            LOG.debug("The updated source of information is not null : {}", patientAllergiesUpdateDTO);

            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientAllergies",
                    "source of Information must be null"
            );
        }
        if (!patientAllergiesUpdateDTO.byPatient() && patientAllergiesUpdateDTO.sourceOfInformation() == null) {
            LOG.debug("The updated source of information is null : {}", patientAllergiesUpdateDTO);

            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientAllergies",
                    "source of Information is required"
            );
        }


        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        if (entity.getCreatedDate().isBefore(todayStart)) {
            LOG.debug("The created date is before today: {}", entity.getCreatedDate());
            throw new BadRequestAlertException(
                    "updateNotAllowed",
                    "patientAllergies",
                    "Only today's records can be updated"
            );
        }

        entity.setAllergenType(patientAllergiesUpdateDTO.allergenType());
        entity.setAllergenId(patientAllergiesUpdateDTO.allergenId());
        entity.setSeverity(patientAllergiesUpdateDTO.severity());
        entity.setMedicationClassId(patientAllergiesUpdateDTO.medicationClassId());
        entity.setCriticality(patientAllergiesUpdateDTO.criticality());
        entity.setCertainty(patientAllergiesUpdateDTO.certainty());
        entity.setTreatmentStrategy(patientAllergiesUpdateDTO.treatmentStrategy());
        entity.setOnset(patientAllergiesUpdateDTO.onset());
        entity.setOnsetDateUndefined(patientAllergiesUpdateDTO.onsetDateUndefined());
        entity.setOnsetDate(patientAllergiesUpdateDTO.onsetDate());
        entity.setTypeOfPropensity(patientAllergiesUpdateDTO.typeOfPropensity());
        entity.setByPatient(patientAllergiesUpdateDTO.byPatient());
        entity.setSourceOfInformation(patientAllergiesUpdateDTO.sourceOfInformation());
        entity.setNote(patientAllergiesUpdateDTO.note());
        entity.setAllergicReactions(patientAllergiesUpdateDTO.allergicReactions());

        if (entity.getAllergenType() == AllergenTypes.MEDICATION) {
            LOG.debug("The updated allergen type is medication : {}", patientAllergiesUpdateDTO);
            List<Long> newIds = patientAllergiesUpdateDTO.activeIngredients() == null
                    ? List.of()
                    : new ArrayList<>(patientAllergiesUpdateDTO.activeIngredients());


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
                    "This allergy already exist"
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
