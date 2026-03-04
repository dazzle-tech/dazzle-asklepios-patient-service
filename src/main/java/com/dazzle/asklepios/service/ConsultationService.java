package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import com.dazzle.asklepios.domain.enumeration.ConsultationLevel;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.consultation.ConsultationCreateDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class ConsultationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsultationService.class);

    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;


    public ConsultationService(
            ConsultationRepository consultationRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository
    ) {
        this.consultationRepository = consultationRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public Consultation create(ConsultationCreateDTO dto) {
        LOG.info("[CREATE] Consultation payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + dto.patientId(),
                                "consultation",
                                "patient.notfound"
                        )
                );
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + dto.encounterId(),
                                "consultation",
                                "encounter.notfound"
                        )
                );



        LOG.debug("[CREATE] Patient found with id={}", patient.getId());

        Consultation entity = Consultation.builder()
                .patient(patient)
                .encounter(encounter)
                .fromFacilityId(dto.fromFacilityId())
                .toFacilityId(dto.toFacilityId())
                .fromDepartmentId(dto.fromDepartmentId())
                .toDepartmentId(dto.toDepartmentId())
                .consultationType(dto.consultationType())
                .destinationType(dto.destinationType())
                .consultantSpeciality(dto.consultantSpeciality())
                .practitionerId(dto.practitionerId())
                .consultationMethod(dto.consultationMethod())
                .consultationLevel(ConsultationLevel.valueOf(dto.consultationLevel()))
                .consultationContent(dto.consultationContent())
                .notes(dto.notes())
                .extraDocument(dto.extraDocument())
                .approvalNumber(dto.approvalNumber())
                .status(ConsultationStatus.REQUESTED)
                .build();

        LOG.debug("[CREATE] Consultation entity built with destinationType={}", entity.getDestinationType());

        try {
            Consultation saved = consultationRepository.saveAndFlush(entity);
            LOG.info("[CREATE] Consultation successfully created with id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.error("[CREATE] Database constraint violation occurred", ex);
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating consultation",
                    "consultation",
                    "db.constraint"
            );
        }
    }

    public Consultation update(Long id, ConsultationUpdateDTO dto) {
        LOG.info("[UPDATE] Consultation id={} payload={}", id, dto);

        Consultation existing = consultationRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Consultation not found with id " + id,
                                "consultation",
                                "notfound"
                        )
                );

        LOG.debug("[UPDATE] Existing consultation found with status={}", existing.getStatus());

        existing.setDestinationType(
                dto.destinationType() != null
                        ? dto.destinationType()
                        : existing.getDestinationType()
        );


        existing.setToFacilityId(dto.toFacilityId());
        existing.setToDepartmentId(dto.toDepartmentId());

        existing.setConsultantSpeciality(dto.consultantSpeciality());
        existing.setPractitionerId(dto.practitionerId());

        existing.setConsultationMethod(dto.consultationMethod());
        existing.setConsultationType(dto.consultationType());
        existing.setConsultationLevel(
                dto.consultationLevel() != null
                        ? ConsultationLevel.valueOf(dto.consultationLevel())
                        : existing.getConsultationLevel()
        );

        existing.setConsultationContent(dto.consultationContent());

        existing.setNotes(dto.notes());
        existing.setExtraDocument(dto.extraDocument());
        existing.setApprovalNumber(dto.approvalNumber());

        LOG.debug("[UPDATE] Consultation fields updated, saving changes");

        try {
            Consultation updated = consultationRepository.saveAndFlush(existing);
            LOG.info("[UPDATE] Consultation successfully updated with id={}", updated.getId());
            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.error("[UPDATE] Database constraint violation occurred", ex);
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating consultation.",
                    "consultation",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<Consultation> findNotCancelled(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_NOT_CANCELLED] encounterId={} pageable={}", encounterId, pageable);
        Page<Consultation> result = consultationRepository.findByEncounterIdAndStatusNot(
                encounterId,
                ConsultationStatus.CANCELLED,
                pageable
        );
        LOG.debug("[FIND_NOT_CANCELLED] Found {} consultations", result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Consultation> findByEncounter(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER] encounterId={} pageable={}", encounterId, pageable);
        Page<Consultation> result = consultationRepository.findByEncounterId(encounterId, pageable);
        LOG.debug("[FIND_BY_ENCOUNTER] Found {} consultations", result.getTotalElements());
        return result;
    }

    public Consultation cancel(
            Long id,
            String cancelReason,
            Long cancelledBy
    ) {
        LOG.info("[CANCEL] Consultation id={} reason={} cancelledBy={}", id, cancelReason, cancelledBy);

        Consultation existing = consultationRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Consultation not found with id " + id,
                                "consultation",
                                "notfound"
                        )
                );

        LOG.debug("[CANCEL] Current consultation status={}", existing.getStatus());

        existing.setStatus(ConsultationStatus.CANCELLED);
        existing.setCancellationReason(cancelReason);
        existing.setCancelledDate(Instant.now());
        existing.setCancelledBy(cancelledBy);

        try {
            Consultation cancelled = consultationRepository.saveAndFlush(existing);
            LOG.info("[CANCEL] Consultation successfully cancelled with id={}", cancelled.getId());
            return cancelled;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.error("[CANCEL] Database constraint violation occurred", ex);
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling consultation.",
                    "consultation",
                    "db.constraint"
            );
        }
    }


    @Transactional(readOnly = true)
    public Page<Consultation> findByEncounterNotCancelled(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_NOT_CANCELLED] encounterId={} pageable={}", encounterId, pageable);
        Page<Consultation> result = consultationRepository.findByEncounterIdAndStatusNot(
                encounterId,
                ConsultationStatus.CANCELLED,
                pageable
        );
        LOG.debug("[FIND_NOT_CANCELLED] Found {} consultations", result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Consultation> findByEncounterWithDateRange(
            Long encounterId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_DATE_RANGE] encounterId={} fromDate={} toDate={} pageable={}",
                encounterId, fromDate, toDate, pageable);
        Page<Consultation> result = consultationRepository.findByEncounterIdAndCreatedDateBetween(
                encounterId,
                fromDate,
                toDate,
                pageable
        );
        LOG.debug("[FIND_DATE_RANGE] Found {} consultations", result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Consultation> findByEncounterWithDateRangeNotCancelled(
            Long encounterId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_DATE_RANGE_NOT_CANCELLED] encounterId={} fromDate={} toDate={} pageable={}",
                encounterId, fromDate, toDate, pageable);
        Page<Consultation> result = consultationRepository.findByEncounterIdAndCreatedDateBetweenAndStatusNot(
                encounterId,
                fromDate,
                toDate,
                ConsultationStatus.CANCELLED,
                pageable
        );
        LOG.debug("[FIND_DATE_RANGE_NOT_CANCELLED] Found {} consultations", result.getTotalElements());
        return result;
    }


    @Transactional(readOnly = true)
    public List<Long> getConsultationDepartmentIdsByEncounterId(Long encounterId) {
        LOG.debug("[GET_DEPARTMENT_IDS] encounterId={}", encounterId);

        List<Long> departmentIds = consultationRepository
                .findByEncounterIdAndDestinationType(
                        encounterId,
                        DestinationType.DEPARTMENT
                )
                .stream()
                .map(Consultation::getToDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        LOG.debug("[GET_DEPARTMENT_IDS] Found {} department IDs", departmentIds.size());
        return departmentIds;
    }

    @Transactional(readOnly = true)
    public List<Long> getConsultationPractitionerIdsByEncounterId(Long encounterId) {
        LOG.debug("[GET_PRACTITIONER_IDS] encounterId={}", encounterId);

        List<Long> practitionerIds = consultationRepository
                .findByEncounterIdAndDestinationType(
                        encounterId,
                        DestinationType.CONSULTANT
                )
                .stream()
                .map(Consultation::getPractitionerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        LOG.debug("[GET_PRACTITIONER_IDS] Found {} practitioner IDs", practitionerIds.size());
        return practitionerIds;
    }

    @Transactional(readOnly = true)
    public Consultation findById(Long id) {
        LOG.debug("[FIND_BY_ID] id={}", id);
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Consultation not found with id " + id,
                                "consultation",
                                "notfound"
                        )
                );
        LOG.debug("[FIND_BY_ID] Consultation found with id={} status={}", id, consultation.getStatus());
        return consultation;
    }


    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        throw new BadRequestAlertException(
                "Database constraint violated while saving consultation.",
                "consultation",
                "db.constraint"
        );
    }

}