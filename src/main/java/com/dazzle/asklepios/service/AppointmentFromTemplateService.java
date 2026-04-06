package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateNoShowDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateSearchFilterDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentFromTemplateService {

    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;

    private static final String ENTITY_NAME = "AppointmentFromTemplate";

    private static final Logger LOG = LoggerFactory.getLogger(ReferralRequestService.class);

    private final PatientRepository patientRepository;

    public AppointmentFromTemplate bookPatientAppointment(
            AppointmentFromTemplateBookPatientDTO dto
    ) {
        log.debug("Request to update AppointmentFromTemplate dto={}", dto);

        AppointmentFromTemplate appointment = appointmentFromTemplateRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment not found with id: " + dto.id(),
                        ENTITY_NAME,
                        "notfound"
                ));

        if (dto.patientId() != null) {
            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id: " + dto.patientId(),
                            ENTITY_NAME,
                            "notfound"
                    ));
            appointment.setPatient(patient);
        }

        if (dto.defaultService() != null) {
            appointment.setDefaultService(dto.defaultService());
        }

        if (dto.defaultPractitioner() != null) {
            appointment.setDefaultPractitioner(dto.defaultPractitioner());
        }

        if (dto.reason() != null) {
            appointment.setReason(dto.reason());
        }

        if (dto.status() != null) {
            appointment.setStatus(dto.status());
        }

        if (dto.note() != null) {
            appointment.setNote(dto.note());
        }

        AppointmentFromTemplate saved = appointmentFromTemplateRepository.save(appointment);

        return saved;
    }

    public Page<AppointmentFromTemplate> getAppointmentsByStatusBetweenDates(
            AppointmentStatus status,
            Instant startDatetime,
            Instant endDatetime,
            Pageable pageable
    ) {
        LOG.debug("Request to get appointments with patient not null between startDatetime={} and endDatetime={}", startDatetime, endDatetime);

        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException("startDatetime and endDatetime are required", "appointmentFormTemplate", "payload.required");
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException("startDatetime must be before or equal to endDatetime", "appointmentFormTemplate", "payload.required");
        }

        return appointmentFromTemplateRepository.findByStatusAndStartDatetimeBetween(
                status,
                startDatetime,
                endDatetime,
                pageable
        );
    }

    public Page<AppointmentFromTemplate> filterAppointment(
            AppointmentFromTemplateSearchFilterDTO filter,
            Pageable pageable
    ) {

        LOG.debug("Service filter Appointments filter={} pageable={}", filter, pageable);

        if (filter.facility() == null) {
            throw new IllegalArgumentException("Facility is required");
        }

        Specification<AppointmentFromTemplate> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facility"), filter.facility()));

            if (filter.department() != null) {
                predicates.add(cb.equal(root.get("department"), filter.department()));
            }

            if (filter.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), filter.resourceType()));
            }

            if (filter.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), filter.resourceId()));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.bookingMode() != null) {
                predicates.add(cb.equal(root.get("bookingMode"), filter.bookingMode()));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AppointmentFromTemplate> result =
                appointmentFromTemplateRepository.findAll(appointmentFilterSpec, pageable);

        LOG.debug("[FILTER] Appointments result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());

        return result;
    }
    private AppointmentFromTemplate getAppointment(Long id) {
        return appointmentFromTemplateRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment not found: " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    public AppointmentFromTemplate cancel(AppointmentFromTemplateCancelDTO dto) {
        AppointmentFromTemplate appointment = getAppointment(dto.id());

        validateCancelable(appointment);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelReason(dto.cancelReason());
        appointment.setCancelledBy(currentUsername());
        return appointmentFromTemplateRepository.save(appointment);
    }

    public AppointmentFromTemplate noShow(AppointmentFromTemplateNoShowDTO dto){
        AppointmentFromTemplate appointment = getAppointment(dto.id());

        validateNoShowable(appointment);

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.setNoShowReason(dto.noShowReason());
        return appointmentFromTemplateRepository.save(appointment);
    }

    @Transactional
    public AppointmentFromTemplate confirm(Long id) {
        AppointmentFromTemplate appointment = getAppointment(id);

        validateConfirmable(appointment);

        if (appointment.getPatient() == null) {
            throw new BadRequestAlertException(
                    "Cannot confirm appointment without patient",
                    ENTITY_NAME,
                    "patientrequired"
            );
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        AppointmentFromTemplate savedAppointment = appointmentFromTemplateRepository.save(appointment);

        PatientEncounterCreateDTO encounterCreateDTO = new PatientEncounterCreateDTO(
                savedAppointment.getPatient().getId(),                  // patientId
                savedAppointment.getFacility(),                         // facilityId
                savedAppointment.getDepartment(),                       // departmentId
                savedAppointment.getDefaultPractitioner(),              // practitionerId
                savedAppointment.getId(),                               // appointmentId
                EncounterType.OUTPATIENT,                               // encounterType
                savedAppointment.getService(),                           // encounterReason
                null,                                                   // followUpEncounterId
                savedAppointment.getPriority(),                         // priorityLevel
                "APPOINTMENT",                                          // originType
                "Appointment Confirmation",                             // originName
                savedAppointment.getNote(),                             // notes
                savedAppointment.getReason(),                           // chiefComplaint
                false,                                                  // hasOrder
                false,                                                  // isObserved
                false,                                                  // hasPrescription
                savedAppointment.getStartDatetime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()                                  // encounterDate
        );

        patientEncounterService.create(encounterCreateDTO);

        return savedAppointment;
    }

    public AppointmentFromTemplate checkIn(Long id) {
        AppointmentFromTemplate appointment = getAppointment(id);

        validateCheckInable(appointment);

        appointment.setStatus(AppointmentStatus.CHECKED_IN);

        return appointmentFromTemplateRepository.save(appointment);
    }
    private void validateCancelable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("Appointment already cancelled", ENTITY_NAME, "alreadycancelled");
        }
       else if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("Checked-in appointment cannot be cancelled", ENTITY_NAME, "invalidstatus");
        }
        else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("IN_SERVICE appointment cannot be cancelled", ENTITY_NAME, "invalidstatus");
        }
        else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("Completed appointment cannot be cancelled", ENTITY_NAME, "invalidstatus");
        }

    }

    private void validateNoShowable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("Cancelled appointment cannot be marked as no-show", ENTITY_NAME, "invalidstatus");
        }
        if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("Checked-in appointment cannot be marked as no-show", ENTITY_NAME, "invalidstatus");
        }
        else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("IN_SERVICE appointment cannot be No-show", ENTITY_NAME, "invalidstatus");
        }
        else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("Completed appointment cannot be No-show", ENTITY_NAME, "invalidstatus");
        }
    }

    private void validateConfirmable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BadRequestAlertException(
                    "Only booked appointments can be confirmed",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }
    }

    private void validateCheckInable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BadRequestAlertException(
                    "Only booked or confirmed appointments can be checked in",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_test_requests",
                        "No authenticated user"
                ));
    }
}
