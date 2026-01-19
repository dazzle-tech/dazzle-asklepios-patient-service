package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
public class SocialHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(SocialHistoryService.class);

    private final SocialHistoryRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }


    public SocialHistory create(SocialHistoryCreateDTO dto) {
        LOG.info("[CREATE] SocialHistory payload={}", dto);


        SocialHistory entity = SocialHistory.builder()
                .patient(refPatient(dto.patientId()))
                .isCurrentSmoker(dto.isCurrentSmoker())
                .smokeStartDate(dto.smokeStartDate())
                .cigaretteAmount(dto.cigaretteAmount())
                .cigaretteType(dto.cigaretteType())
                .isPreviousSmoker(dto.isPreviousSmoker())
                .smokeQuitDate(dto.smokeQuitDate())
                .exposureToSecondHandSmoke(dto.exposureToSecondHandSmoke())
                .alcoholConsumption(dto.alcoholConsumption())
                .typeOfAlcohol(dto.typeOfAlcohol())
                .alcoholSinceWhen(dto.alcoholSinceWhen())
                .substanceUse(dto.substanceUse())
                .route(dto.route())
                .frequency(dto.frequency())
                .physicalLimitation(dto.physicalLimitation())
                .diagnosedEatingDisorders(dto.diagnosedEatingDisorders())
                .build();

        try {
            SocialHistory saved = repository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating social history.",
                    "socialHistory",
                    "db.constraint"
            );
        }
    }


    public SocialHistory update(SocialHistoryUpdateDTO dto) {

        SocialHistory entity = repository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Social history not found with id " + dto.id(),
                        "socialHistory",
                        "notfound"
                ));

        entity.setPatient(refPatient(dto.patientId()));
        entity.setIsCurrentSmoker(dto.isCurrentSmoker());
        entity.setIsPreviousSmoker(dto.isPreviousSmoker());
        entity.setAlcoholConsumption(dto.alcoholConsumption());
        entity.setSubstanceUse(dto.substanceUse());

        entity.setSmokeStartDate(dto.smokeStartDate());
        entity.setCigaretteAmount(dto.cigaretteAmount());
        entity.setCigaretteType(dto.cigaretteType());
        entity.setSmokeQuitDate(dto.smokeQuitDate());
        entity.setAlcoholSinceWhen(dto.alcoholSinceWhen());
        entity.setTypeOfAlcohol(dto.typeOfAlcohol());
        entity.setRoute(dto.route());
        entity.setFrequency(dto.frequency());
        entity.setExposureToSecondHandSmoke(dto.exposureToSecondHandSmoke());
        entity.setPhysicalLimitation(dto.physicalLimitation());
        entity.setDiagnosedEatingDisorders(dto.diagnosedEatingDisorders());

        try {
            SocialHistory updated = repository.saveAndFlush(entity);
            entityManager.refresh(updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating social history.",
                    "socialHistory",
                    "db.constraint"
            );
        }
    }


    public void delete(Long id) {
        LOG.info("[DELETE] SocialHistory id={}", id);

        SocialHistory entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Social history not found with id " + id,
                        "socialHistory",
                        "notfound"
                ));

        repository.delete(entity);
    }


    @Transactional(readOnly = true)
    public Page<SocialHistory> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] SocialHistory patientId={} pageable={}", patientId, pageable);
        return repository.findAllByPatientId(patientId, pageable);
    }


    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);
        String lower = message != null ? message.toLowerCase() : "";

        if (lower.contains("ux_social_history_patient")) {
            throw new BadRequestAlertException(
                    "Social history already exists for this patient.",
                    "socialHistory",
                    "duplicate"
            );
        }

        if (lower.contains("ck_social_history_current_smoker_required")) {
            throw new BadRequestAlertException(
                    "Start date and cigarette amount are required for current smoker.",
                    "socialHistory",
                    "current.smoker.required"
            );
        }

        if (lower.contains("ck_social_history_previous_smoker_required")) {
            throw new BadRequestAlertException(
                    "Quit date is required for previous smoker.",
                    "socialHistory",
                    "previous.smoker.required"
            );
        }

        if (lower.contains("ck_social_history_smoker_xor")) {
            throw new BadRequestAlertException(
                    "Patient cannot be current smoker and previous smoker at the same time.",
                    "socialHistory",
                    "smoker.conflict"
            );
        }

        if (lower.contains("ck_social_history_alcohol_since_when_required")) {
            throw new BadRequestAlertException(
                    "Alcohol since-when date is required when alcohol consumption is enabled.",
                    "socialHistory",
                    "alcohol.since.required"
            );
        }

        if (lower.contains("foreign key") && lower.contains("patient")) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "socialHistory",
                    "patient.invalid"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving social history.",
                "socialHistory",
                "db.constraint"
        );
    }
}
