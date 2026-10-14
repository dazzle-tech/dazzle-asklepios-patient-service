package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivitySource;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.PatientActivityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCatalogVM;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCancelDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCompleteDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCreateDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientActivityService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientActivityService.class);
    private static final String ENTITY = "patientActivity";

    private final PatientActivityRepository patientActivityRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public PatientActivityCatalogVM catalog() {
        return PatientActivityCatalogVM.fromEnums();
    }

    public PatientActivity create(PatientActivityCreateDTO dto) {
        LOG.info("[CREATE] PatientActivity patientId={} type={}", dto.getPatientId(), dto.getActivityType());

        validatePatient(dto.getPatientId());
        validateTypeMatchesCategory(dto.getActivityType(), dto.getCategory());
        validateStatusOnCreate(dto.getStatus());
        validateDueDate(dto.getStatus(), dto.getDueAt());
        validateEncounter(dto.getPatientId(), dto.getEncounterId());
        validateAppointment(dto.getPatientId(), dto.getAppointmentId());

        PatientActivityStatus status = dto.getStatus();
        String currentUser = currentUsername();

        PatientActivity activity = PatientActivity.builder()
                .patientId(dto.getPatientId())
                .encounterId(dto.getEncounterId())
                .appointmentId(dto.getAppointmentId())
                .category(dto.getCategory())
                .activityType(dto.getActivityType())
                .status(status)
                .source(PatientActivitySource.STAFF)
                .priority(resolvePriority(dto.getActivityType(), dto.getPriority()))
                .note(dto.getNote().trim())
                .dueAt(dto.getDueAt())
                .build();

        if (status == PatientActivityStatus.DONE) {
            activity.setCompletedAt(Instant.now());
            activity.setCompletedBy(currentUser);
        }

        PatientActivity saved = patientActivityRepository.save(activity);
        LOG.info("[CREATE] PatientActivity id={} patientId={}", saved.getId(), saved.getPatientId());
        return saved;
    }

    public PatientActivity update(Long id, PatientActivityUpdateDTO dto) {
        LOG.info("[UPDATE] PatientActivity id={}", id);

        PatientActivity existing = findOpenStaffActivity(id);
        validateTypeMatchesCategory(dto.getActivityType(), dto.getCategory());
        validateDueDate(PatientActivityStatus.OPEN, dto.getDueAt());
        validateEncounter(existing.getPatientId(), dto.getEncounterId());
        validateAppointment(existing.getPatientId(), dto.getAppointmentId());

        existing.setEncounterId(dto.getEncounterId());
        existing.setAppointmentId(dto.getAppointmentId());
        existing.setCategory(dto.getCategory());
        existing.setActivityType(dto.getActivityType());
        existing.setPriority(resolvePriority(dto.getActivityType(), dto.getPriority()));
        existing.setNote(dto.getNote().trim());
        existing.setDueAt(dto.getDueAt());

        return patientActivityRepository.save(existing);
    }

    public PatientActivity complete(Long id, PatientActivityCompleteDTO dto) {
        LOG.info("[COMPLETE] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        existing.setStatus(PatientActivityStatus.DONE);
        existing.setCompletedAt(Instant.now());
        existing.setCompletedBy(currentUsername());
        existing.setOutcomeNote(dto.getOutcomeNote().trim());
        return patientActivityRepository.save(existing);
    }

    public PatientActivity cancel(Long id, PatientActivityCancelDTO dto) {
        LOG.info("[CANCEL] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        existing.setStatus(PatientActivityStatus.CANCELLED);
        existing.setCancelledAt(Instant.now());
        existing.setCancelledBy(currentUsername());
        existing.setCancellationReason(dto.getCancellationReason().trim());
        return patientActivityRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public PatientActivity findById(Long id) {
        return patientActivityRepository.findById(id)
                .orElseThrow(() -> bad("Activity not found.", "patientActivity.notFound"));
    }

    @Transactional(readOnly = true)
    public Page<PatientActivity> findByPatient(
            Long patientId,
            PatientActivityCategory category,
            PatientActivityStatus status,
            Pageable pageable
    ) {
        validatePatient(patientId);
        Pageable sorted = withDefaultSort(pageable);

        if (category != null && status != null) {
            return patientActivityRepository.findByPatientIdAndCategoryAndStatus(patientId, category, status, sorted);
        }
        if (category != null) {
            return patientActivityRepository.findByPatientIdAndCategory(patientId, category, sorted);
        }
        if (status != null) {
            return patientActivityRepository.findByPatientIdAndStatus(patientId, status, sorted);
        }
        return patientActivityRepository.findByPatientId(patientId, sorted);
    }

    private PatientActivity findOpenStaffActivity(Long id) {
        PatientActivity existing = findOpenActivity(id);
        if (existing.getSource() != PatientActivitySource.STAFF) {
            throw bad("System activities cannot be edited.", "patientActivity.systemNotEditable");
        }
        return existing;
    }

    private PatientActivity findOpenActivity(Long id) {
        if (id == null || id <= 0) {
            throw bad("Activity id is required.", "patientActivity.id.invalid");
        }

        PatientActivity existing = patientActivityRepository.findById(id)
                .orElseThrow(() -> bad("Activity not found.", "patientActivity.notFound"));

        if (existing.getStatus() != PatientActivityStatus.OPEN) {
            throw bad("Only an open activity can be changed.", "patientActivity.notOpen");
        }
        return existing;
    }

    private void validatePatient(Long patientId) {
        if (patientId == null || patientId <= 0) {
            throw bad("Patient id is required.", "patient.id.invalid");
        }
        if (!patientRepository.existsById(patientId)) {
            throw bad("Patient not found.", "patient.notFound");
        }
    }

    private void validateTypeMatchesCategory(PatientActivityType activityType, PatientActivityCategory category) {
        if (activityType == null || category == null || !activityType.belongsTo(category)) {
            throw bad("Activity type does not belong to the selected category.", "patientActivity.type.mismatch");
        }
    }

    private void validateStatusOnCreate(PatientActivityStatus status) {
        if (status != PatientActivityStatus.OPEN && status != PatientActivityStatus.DONE) {
            throw bad("A new activity can only be open or done.", "patientActivity.status.invalid");
        }
    }

    private void validateDueDate(PatientActivityStatus status, Instant dueAt) {
        if (status == PatientActivityStatus.OPEN && dueAt == null) {
            throw bad("Due date is required for an open activity.", "patientActivity.dueDate.required");
        }
    }

    private void validateEncounter(Long patientId, Long encounterId) {
        if (encounterId == null) {
            return;
        }
        if (encounterId <= 0) {
            throw bad("Encounter id is invalid.", "encounter.id.invalid");
        }

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> bad("Encounter not found.", "encounter.notFound"));

        if (encounter.getPatient() == null || !patientId.equals(encounter.getPatient().getId())) {
            throw bad("Encounter does not belong to this patient.", "encounter.patient.mismatch");
        }
    }

    private void validateAppointment(Long patientId, Long appointmentId) {
        if (appointmentId == null) {
            return;
        }
        if (appointmentId <= 0) {
            throw bad("Appointment id is invalid.", "appointment.id.invalid");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> bad("Appointment not found.", "appointment.notFound"));

        if (appointment.getPatient() == null || !patientId.equals(appointment.getPatient().getId())) {
            throw bad("Appointment does not belong to this patient.", "appointment.patient.mismatch");
        }
    }

    private PatientActivityPriority resolvePriority(PatientActivityType activityType, PatientActivityPriority priority) {
        if (priority != null) {
            return priority;
        }
        return activityType.defaultPriority();
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }

    private BadRequestAlertException bad(String message, String errorKey) {
        return new BadRequestAlertException(message, ENTITY, errorKey);
    }
}
