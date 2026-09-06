package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.security.SecurityUtils;
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
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryCancelDTO;

import java.time.Instant;
import java.util.Date;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;

@Service
@RequiredArgsConstructor
@Transactional
public class SurgicalHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(SurgicalHistoryService.class);

    private final SurgicalHistoryRepository repository;
    private final PatientRepository patientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Patient refPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "SurgicalHistory",
                        "patient.notfound"
                ));
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "surgicalHistory",
                        "No authenticated user"
                ));
    }

    public SurgicalHistory create(
            SurgicalHistoryCreateDTO surgicalHistoryCreateDTO
    ) {
        LOG.info("[CREATE] SurgicalHistory payload={}", surgicalHistoryCreateDTO);
        validateRequiredFields(surgicalHistoryCreateDTO);
        SurgicalHistory entity = SurgicalHistory.builder()
                .patient(refPatient(surgicalHistoryCreateDTO.patientId()))
                .surgery(surgicalHistoryCreateDTO.surgery())
                .dateOfSurgery(surgicalHistoryCreateDTO.dateOfSurgery())
                .facility(surgicalHistoryCreateDTO.facility())
                .anesthesiaType(surgicalHistoryCreateDTO.anesthesiaType())
                .complications(surgicalHistoryCreateDTO.complications())
                .patientIsFree(surgicalHistoryCreateDTO.patientIsFree())
                .adverseReactionsToAnesthesia(
                        surgicalHistoryCreateDTO.adverseReactionsToAnesthesia()
                )
                .hasImplantsOrDevices(
                        surgicalHistoryCreateDTO.hasImplantsOrDevices()
                )
                .implantsOrDevicesDescription(
                        surgicalHistoryCreateDTO.implantsOrDevicesDescription()
                )

                // Default status for new records
                .status(PatientHistoryStatus.ACTIVE)

                .build();

        try {
            return repository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving surgical history.",
                    "surgicalHistory",
                    "db.constraint"
            );
        }
    }

    public SurgicalHistory update(SurgicalHistoryUpdateDTO dto) {
        LOG.info("[UPDATE] SurgicalHistory payload={}", dto);
        validateRequiredFields(dto);

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
        entity.setPatientIsFree(dto.patientIsFree());

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

    public SurgicalHistory cancel(SurgicalHistoryCancelDTO dto) {
        LOG.info("[CANCEL] SurgicalHistory payload={}", dto);

        SurgicalHistory entity = repository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Surgical history not found with id " + dto.id(),
                        "surgicalHistory",
                        "notfound"
                ));

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(dto.cancellationReason());

        try {
            SurgicalHistory cancelled = repository.saveAndFlush(entity);
            entityManager.refresh(cancelled);
            return cancelled;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling surgical history.",
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
    public Page<SurgicalHistory> findByPatientId(
            Long patientId,
            boolean showCancelled,
            Pageable pageable
    ) {
        LOG.debug(
                "[LIST] SurgicalHistory patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        if (showCancelled) {
            return repository.findAllByPatientId(
                    patientId,
                    pageable
            );
        }

        return repository.findAllByPatientIdAndStatusNot(
                patientId,
                PatientHistoryStatus.CANCELLED,
                pageable
        );
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




        throw new BadRequestAlertException(
                "Database constraint violated while saving surgical history.",
                "surgicalHistory",
                "db.constraint"
        );

    }
    private void validateRequiredFields(SurgicalHistoryCreateDTO dto) {

        if (Boolean.TRUE.equals(dto.patientIsFree())) {
            return;
        }

        if (dto.surgery() == null || dto.surgery().isBlank()) {
            throw new BadRequestAlertException(
                    "Surgery is required.",
                    "surgicalHistory",
                    "surgery.required"
            );
        }

        if (dto.dateOfSurgery() == null) {
            throw new BadRequestAlertException(
                    "Date of surgery is required.",
                    "surgicalHistory",
                    "dateOfSurgery.required"
            );
        }

        if (dto.facility() == null || dto.facility().isBlank()) {
            throw new BadRequestAlertException(
                    "Facility is required.",
                    "surgicalHistory",
                    "facility.required"
            );
        }

        if (dto.anesthesiaType() == null || dto.anesthesiaType().isBlank()) {
            throw new BadRequestAlertException(
                    "Anesthesia type is required.",
                    "surgicalHistory",
                    "anesthesia.required"
            );
        }
    }
    private void validateRequiredFields(SurgicalHistoryUpdateDTO dto) {

        if (Boolean.TRUE.equals(dto.patientIsFree())) {
            return;
        }

        if (dto.surgery() == null || dto.surgery().isBlank()) {
            throw new BadRequestAlertException(
                    "Surgery is required.",
                    "surgicalHistory",
                    "surgery.required"
            );
        }

        if (dto.dateOfSurgery() == null) {
            throw new BadRequestAlertException(
                    "Date of surgery is required.",
                    "surgicalHistory",
                    "dateOfSurgery.required"
            );
        }

        if (dto.facility() == null || dto.facility().isBlank()) {
            throw new BadRequestAlertException(
                    "Facility is required.",
                    "surgicalHistory",
                    "facility.required"
            );
        }

        if (dto.anesthesiaType() == null || dto.anesthesiaType().isBlank()) {
            throw new BadRequestAlertException(
                    "Anesthesia type is required.",
                    "surgicalHistory",
                    "anesthesia.required"
            );
        }
    }
}