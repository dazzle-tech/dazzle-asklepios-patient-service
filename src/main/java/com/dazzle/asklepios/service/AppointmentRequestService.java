package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentRequest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentRequestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointment.AppointmentBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.AppointmentRequestResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
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
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;

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
        facilityHelper.validateFacilityExists(dto.facilityId());
        departmentHelper.validateDepartmentExists(dto.departmentId());


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
        request.setPreferredDate(dto.preferredDate());

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    public AppointmentRequestResponseVM approve(AppointmentRequestUpdateDTO dto) {
        log.debug("Request to approve AppointmentRequest dto={}", dto);

        AppointmentRequest request = appointmentRequestRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + dto.id(),
                        ENTITY_NAME,
                        "id.notfound"
                ));

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled request cannot be approved",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }

        if (request.getStatus() == AppointmentRequestStatus.APPROVED) {
            throw new BadRequestAlertException(
                    "Appointment request already approved",
                    ENTITY_NAME,
                    "alreadyapproved"
            );
        }

        if (dto.appointmentId() == null) {
            throw new BadRequestAlertException(
                    "Appointment id is required for approval",
                    ENTITY_NAME,
                    "appointmentidrequired"
            );
        }

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

        Appointment appointment = appointmentRepository.findById(dto.appointmentId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment not found with id: " + dto.appointmentId(),
                        ENTITY_NAME,
                        "appointment.notfound"
                ));

        AppointmentBookPatientDTO bookDto = new AppointmentBookPatientDTO(
                appointment.getId(),
                patient.getId(),
                appointment.getDefaultServiceId(),
                appointment.getDefaultPractitionerId(),
                dto.reason(),
                AppointmentStatus.BOOKED,
                dto.note(),
                EncounterReason.FOLLOW_UP,
                dto.priority(),
                null,
                null,
                sourceEncounter.getId()
        );

        Appointment bookedAppointment = appointmentService.bookPatientAppointment(bookDto);

        request.setPatient(patient);
        request.setFacilityId(dto.facilityId());
        request.setDepartmentId(dto.departmentId());
        request.setSourceEncounter(sourceEncounter);
        request.setRequestedResourceType(dto.requestedResourceType());
        request.setRequestedResourceId(dto.requestedResourceId());
        request.setPriority(dto.priority());
        request.setReason(dto.reason());
        request.setNote(dto.note());
        request.setAppointment(bookedAppointment);
        request.setStatus(AppointmentRequestStatus.APPROVED);

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
    public List<AppointmentRequestResponseVM> getByFacilityIdAndBookableDepartment(Long facilityId) {
        log.debug("Request to get AppointmentRequests by facilityId={}", facilityId);

        String login = SecurityUtils
                .getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "user",
                        ENTITY_NAME,
                        "Current user is required"
                ));

        List<Long> bookableDepartmentIds =departmentHelper.getBookableDepartment().stream().map(DepartmentDTO::id).toList();

        if (bookableDepartmentIds == null || bookableDepartmentIds.isEmpty()) {
            log.debug("[APPOINTMENT_REQUEST] No bookable departments found for logged-in user={}", login);
            return Collections.emptyList();
        }

        return appointmentRequestRepository
                .findByFacilityIdAndDepartmentIdIn(facilityId, bookableDepartmentIds)
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

    public AppointmentRequestResponseVM approve(Long id) {
        log.debug("Request to approve AppointmentRequest id={}", id);

        AppointmentRequest request = getRequest(id);

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled request cannot be approved",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }

        if (request.getStatus() == AppointmentRequestStatus.APPROVED) {
            throw new BadRequestAlertException(
                    "Appointment request already approved",
                    ENTITY_NAME,
                    "alreadyapproved"
            );
        }

        request.setStatus(AppointmentRequestStatus.APPROVED);

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
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
        request.setCancelReason(dto.cancelReason());
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
        String patientMrn = entity.getPatient() != null ? entity.getPatient().getMedicalRecordNumber() : null;
        Long sourceEncounterId = entity.getSourceEncounter() != null ? entity.getSourceEncounter().getId() : null;
        Long appointmentId = entity.getAppointment() != null ? entity.getAppointment().getId() : null;

        return new AppointmentRequestResponseVM(
                entity.getId(),
                patientId,
                patientName,
                patientMrn,
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
                entity.getCancelledAt(),
                entity.getCancelReason(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate(),
                entity.getPreferredDate()
        );
    }
}