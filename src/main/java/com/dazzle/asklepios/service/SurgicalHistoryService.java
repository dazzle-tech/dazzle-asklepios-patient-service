package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryUpdateDTO;
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
public class SurgicalHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(SurgicalHistoryService.class);

    private final SurgicalHistoryRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }

    public SurgicalHistory create(SurgicalHistoryCreateDTO dto) {
        LOG.info("[CREATE] SurgicalHistory payload={}", dto);


        SurgicalHistory entity = SurgicalHistory.builder()
                .patient(refPatient(dto.patientId()))
                .surgery(dto.surgery())
                .dateOfSurgery(dto.dateOfSurgery())
                .facility(dto.facility())
                .anesthesiaType(dto.anesthesiaType())
                .complications(dto.complications())
                .adverseReactionsToAnesthesia(dto.adverseReactionsToAnesthesia())
                .hasImplantsOrDevices(dto.hasImplantsOrDevices())
                .implantsOrDevicesDescription(dto.implantsOrDevicesDescription())
                .build();

        try {
            SurgicalHistory saved = repository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating surgical history.",
                    "surgicalHistory",
                    "db.constraint"
            );
        }
    }

    public SurgicalHistory update(SurgicalHistoryUpdateDTO dto) {
        LOG.info("[UPDATE] SurgicalHistory payload={}", dto);

        SurgicalHistory entity = repository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Surgical history not found with id " + dto.id(),
                        "surgicalHistory",
                        "notfound"
                ));

        entity.setPatient(refPatient(dto.patientId()));
        entity.setSurgery(dto.surgery());
        entity.setDateOfSurgery(dto.dateOfSurgery());
        entity.setFacility(dto.facility());
        entity.setAnesthesiaType(dto.anesthesiaType());
        entity.setComplications(dto.complications());
        entity.setAdverseReactionsToAnesthesia(dto.adverseReactionsToAnesthesia());
        entity.setHasImplantsOrDevices(dto.hasImplantsOrDevices());
        entity.setImplantsOrDevicesDescription(dto.implantsOrDevicesDescription());

        try {
            SurgicalHistory updated = repository.saveAndFlush(entity);
            entityManager.refresh(updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating surgical history.",
                    "surgicalHistory",
                    "db.constraint"
            );
        }
    }


    public void delete(Long id) {
        LOG.info("[DELETE] SurgicalHistory id={}", id);

        SurgicalHistory entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Surgical history not found with id " + id,
                        "surgicalHistory",
                        "notfound"
                ));

        repository.delete(entity);
    }


    @Transactional(readOnly = true)
    public Page<SurgicalHistory> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] SurgicalHistory patientId={} pageable={}", patientId, pageable);
        return repository.findAllByPatientId(patientId, pageable);
    }


    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ux_surgical_history_patient_surgery_date_facility")) {
            throw new BadRequestAlertException(
                    "Surgical history already exists for this patient, surgery, date and facility.",
                    "surgicalHistory",
                    "duplicate"
            );
        }

        if (lower.contains("foreign key") && lower.contains("patient")) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "surgicalHistory",
                    "patient.invalid"
            );
        }

        if (lower.contains("anesthesia_type") && lower.contains("not null")) {
            throw new BadRequestAlertException(
                    "Anesthesia type is required.",
                    "surgicalHistory",
                    "anesthesia.required"
            );
        }


        throw new BadRequestAlertException(
                "Database constraint violated while saving surgical history.",
                "surgicalHistory",
                "db.constraint"
        );
    }
}

