package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.FamilyHistoryRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryUpdateDTO;
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
public class FamilyHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FamilyHistoryService.class);

    private final FamilyHistoryRepository familyHistoryRepository;
    private final PatientRepository patientRepository;


    private Patient refPatient(Long patientId) {
        return patientRepository.getReferenceById(patientId);
    }


    public FamilyHistory create(FamilyHistoryCreateDTO familyHistoryCreateDTO) {
        LOG.info("[CREATE] FamilyHistory payload={}", familyHistoryCreateDTO);

        FamilyHistory entity = FamilyHistory.builder()
                .patient(refPatient(familyHistoryCreateDTO.patientId()))
                .condition(familyHistoryCreateDTO.condition())
                .relation(familyHistoryCreateDTO.relation())
                .inheritedDiseases(familyHistoryCreateDTO.inheritedDiseases())
                .build();

        try {
            FamilyHistory saved = familyHistoryRepository.saveAndFlush(entity);

            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating family history.",
                    "familyHistory",
                    "db.constraint"
            );
        }
    }


    public FamilyHistory update(FamilyHistoryUpdateDTO familyHistoryUpdateDTO) {
        LOG.info("[UPDATE] FamilyHistory payload={}", familyHistoryUpdateDTO);

        FamilyHistory entity = familyHistoryRepository.findById(familyHistoryUpdateDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Family history not found with id " + familyHistoryUpdateDTO.id(),
                        "familyHistory",
                        "notfound"
                ));

        entity.setPatient(refPatient(familyHistoryUpdateDTO.patientId()));
        entity.setCondition(familyHistoryUpdateDTO.condition());
        entity.setRelation(familyHistoryUpdateDTO.relation());
        entity.setInheritedDiseases(familyHistoryUpdateDTO.inheritedDiseases());

        try {
            FamilyHistory updated = familyHistoryRepository.saveAndFlush(entity);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating family history.",
                    "familyHistory",
                    "db.constraint"
            );
        }
    }


    public void delete(Long id) {
        LOG.info("[DELETE] FamilyHistory id={}", id);

        FamilyHistory entity = familyHistoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Family history not found with id " + id,
                        "familyHistory",
                        "notfound"
                ));

        familyHistoryRepository.delete(entity);
    }


    @Transactional(readOnly = true)
    public Page<FamilyHistory> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] FamilyHistory patientId={} pageable={}", patientId, pageable);
        return familyHistoryRepository.findAllByPatientId(patientId, pageable);
    }


    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ux_patientid_condition_relation")
                || (lower.contains("unique") && lower.contains("condition"))) {
            throw new BadRequestAlertException(
                    "Family history entry already exists for this patient, condition and relation.",
                    "familyHistory",
                    "duplicate"
            );
        }

        if (lower.contains("fk_family_history_patient")
                || (lower.contains("foreign key") && lower.contains("patient"))) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "familyHistory",
                    "patient.invalid"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving family history.",
                "familyHistory",
                "db.constraint"
        );
    }
}
