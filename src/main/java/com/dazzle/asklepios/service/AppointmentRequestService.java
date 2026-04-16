package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.AppointmentRequest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import com.dazzle.asklepios.repository.AppointmentRequestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.AppointmentRequestResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AppointmentRequestService {

    private static final String ENTITY_NAME = "appointmentRequest";

    private final AppointmentRequestRepository appointmentRequestRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;

    public AppointmentRequestResponseVM create(AppointmentRequestCreateDTO dto) {
        log.debug("Request to create AppointmentRequest dto={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id: " + dto.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));

        PatientEncounter sourceEncounter = patientEncounterRepository.findById(dto.sourceEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id: " + dto.sourceEncounterId(),
                        ENTITY_NAME,
                        "sourceEncounter.notfound"
                ));

        AppointmentRequest request = new AppointmentRequest();
        request.setPatient(patient);
        request.setFacilityId(dto.facilityId());
        request.setDepartmentId(dto.departmentId());
        request.setSourceEncounter(sourceEncounter);
        request.setRequestedResourceType(dto.requestedResourceType());
        request.setRequestedResourceId(dto.requestedResourceId());
        request.setPriority(dto.priority());
        request.setReason(dto.reason());
        request.setNote(dto.note());
        request.setStatus(AppointmentRequestStatus.REQUESTED);

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    public AppointmentRequestResponseVM update(AppointmentRequestUpdateDTO dto) {
        log.debug("Request to update AppointmentRequest dto={}", dto);

        AppointmentRequest request = appointmentRequestRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + dto.id(),
                        ENTITY_NAME,
                        "id.notfound"
                ));

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id: " + dto.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));

        PatientEncounter sourceEncounter = patientEncounterRepository.findById(dto.sourceEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id: " + dto.sourceEncounterId(),
                        ENTITY_NAME,
                        "sourceEncounter.notfound"
                ));

        request.setPatient(patient);
        request.setFacilityId(dto.facilityId());
        request.setDepartmentId(dto.departmentId());
        request.setSourceEncounter(sourceEncounter);
        request.setRequestedResourceType(dto.requestedResourceType());
        request.setRequestedResourceId(dto.requestedResourceId());
        request.setPriority(dto.priority());
        request.setReason(dto.reason());
        request.setNote(dto.note());
        request.setStatus(dto.status());
        request.setCancelReason(dto.cancelReason());

        if (dto.appointmentId() != null) {
            AppointmentFromTemplate appointment = appointmentFromTemplateRepository.findById(dto.appointmentId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Appointment not found with id: " + dto.appointmentId(),
                            ENTITY_NAME,
                            "appointment.notfound"
                    ));
            request.setAppointment(appointment);
        } else {
            request.setAppointment(null);
        }

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentRequestResponseVM getById(Long id) {
        log.debug("Request to get AppointmentRequest id={}", id);

        AppointmentRequest request = appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));

        return toResponseVM(request);
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getAll() {
        log.debug("Request to get all AppointmentRequests");

        return appointmentRequestRepository.findAll()
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByPatientId(Long patientId) {
        log.debug("Request to get AppointmentRequests by patientId={}", patientId);

        return appointmentRequestRepository.findByPatientId(patientId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByFacilityId(Long facilityId) {
        log.debug("Request to get AppointmentRequests by facilityId={}", facilityId);

        return appointmentRequestRepository.findByFacilityId(facilityId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getBySourceEncounterId(Long sourceEncounterId) {
        log.debug("Request to get AppointmentRequests by sourceEncounterId={}", sourceEncounterId);

        return appointmentRequestRepository.findBySourceEncounterId(sourceEncounterId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByStatus(AppointmentRequestStatus status) {
        log.debug("Request to get AppointmentRequests by status={}", status);

        return appointmentRequestRepository.findByStatus(status)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }


    public AppointmentRequestResponseVM cancel(Long id, AppointmentRequestCancelDTO dto) {
        log.debug("Request to cancel AppointmentRequest id={} dto={}", id, dto);

        AppointmentRequest request = getRequest(id);

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Appointment request already cancelled",
                    ENTITY_NAME,
                    "alreadycancelled"
            );
        }

        request.setStatus(AppointmentRequestStatus.CANCELLED);
        request.setCancelReason(dto.reason());
        request.setCancelledAt(Instant.now());

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    public void delete(Long id) {
        log.debug("Request to delete AppointmentRequest id={}", id);

        AppointmentRequest request = getRequest(id);
        appointmentRequestRepository.delete(request);
    }

    private AppointmentRequest getRequest(Long id) {
        return appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));
    }

    private AppointmentRequestResponseVM toResponseVM(AppointmentRequest entity) {
        Long patientId = entity.getPatient() != null ? entity.getPatient().getId() : null;
        String patientName = entity.getPatient() != null ? entity.getPatient().getFirstName() + "" + entity.getPatient().getLastName() : null;

        Long sourceEncounterId = entity.getSourceEncounter() != null ? entity.getSourceEncounter().getId() : null;
        Long appointmentId = entity.getAppointment() != null ? entity.getAppointment().getId() : null;

        return new AppointmentRequestResponseVM(
                entity.getId(),
                patientId,
                patientName,
                entity.getFacilityId(),
                null,
                entity.getDepartmentId(),
                null,
                sourceEncounterId,
                appointmentId,
                entity.getRequestedResourceType(),
                entity.getRequestedResourceId(),
                entity.getPriority(),
                entity.getReason(),
                entity.getNote(),
                entity.getStatus(),
                entity.getConvertedAt(),
                entity.getCancelledAt(),
                entity.getCancelReason(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        ENTITY_NAME,
                        "user.notauthenticated"
                ));
    }
}