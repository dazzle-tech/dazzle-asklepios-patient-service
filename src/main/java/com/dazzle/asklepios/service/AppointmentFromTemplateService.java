package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.AppointmentLog;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticTest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import com.dazzle.asklepios.repository.AppointmentLogRepository;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateNoShowDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateQuickAppointmentDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateSearchFilterDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.helper.CatalogHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate.AppointmentFromTemplateQuickAppointmentResponseVM;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentFromTemplateService {

    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;
    private final AppointmentLogRepository appointmentLogRepository;

    private static final String ENTITY_NAME = "AppointmentFromTemplate";

    private static final Logger LOG = LoggerFactory.getLogger(ReferralRequestService.class);

    private final PatientRepository patientRepository;
    private final DepartmentHelper departmentHelper;
    private final PatientEncounterService patientEncounterService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final DiagnosticTestHelper diagnosticTestHelper;
    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final CatalogHelper catalogHelper;

    public List<AppointmentLog> getAppointmentLogs(Long appointmentId) {
        LOG.debug("Request to get AppointmentFromTemplate Log id={}", appointmentId);
        return appointmentLogRepository.findAllByAppointmentIdOrderByLogDateDesc(appointmentId);
    }

    public AppointmentFromTemplate bookPatientAppointment(AppointmentFromTemplateBookPatientDTO dto) {
        LOG.debug("Request to update AppointmentFromTemplate dto={}", dto);

        AppointmentFromTemplate appointment = appointmentFromTemplateRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Appointment not found with id: " + dto.id()));

        if (dto.patientId() != null) {
            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Patient not found with id: " + dto.patientId()));
            appointment.setPatient(patient);
        }
        if (dto.defaultService() != null) {
            appointment.setDefaultServiceId(dto.defaultService());
        }
        if (dto.defaultPractitioner() != null) {
            appointment.setDefaultPractitionerId(dto.defaultPractitioner());
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
        appointment.setService(dto.service());

        appointment.setPriority(dto.priority());

        if (dto.originType() != null) {
            appointment.setOriginType(dto.originType());
        }

        if (dto.originName() != null) {
            appointment.setOriginName(dto.originName());
        }
        if (dto.service() == EncounterReason.FOLLOW_UP && dto.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(dto.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Patient Encounter not found with id: " + dto.followUpEncounterId()));

            appointment.setFollowUpEncounter(followUpEncounter);
        }
        return appointmentFromTemplateRepository.save(appointment);
    }

    public Page<AppointmentFromTemplate> getAppointmentsByStatusBetweenDates(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime, Pageable pageable) {
        LOG.debug("Request to get appointments with patient not null between startDatetime={} and endDatetime={}", startDatetime, endDatetime);

        return appointmentFromTemplateRepository.findByStatusInAndStartDatetimeBetween(status, startDatetime, endDatetime, pageable);
    }

    public Page<AppointmentFromTemplate> filterAppointment(AppointmentFromTemplateSearchFilterDTO filter, Pageable pageable) {

        LOG.debug("Service filter Appointments filter={} pageable={}", filter, pageable);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        Specification<AppointmentFromTemplate> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            if (filter.department() != null) {
                predicates.add(cb.equal(root.get("departmentId"), filter.department()));
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

            if (filter.bookingMode() != null && !filter.bookingMode().isEmpty()) {
                predicates.add(root.get("bookingMode").in(filter.bookingMode()));
            }
            else {
                predicates.add(cb.notEqual(root.get("bookingMode"), BookingMode.BUFFER));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AppointmentFromTemplate> result = appointmentFromTemplateRepository.findAll(appointmentFilterSpec, pageable);

        LOG.debug("[FILTER] Appointments result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());

        return result;
    }

    public AppointmentFromTemplate cancel(AppointmentFromTemplateCancelDTO dto) {
        AppointmentFromTemplate appointment = getAppointment(dto.id());

        validateCancelable(appointment);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelReason(dto.cancelReason());
        appointment.setCancelledBy(currentUsername());
        return appointmentFromTemplateRepository.save(appointment);
    }

    public AppointmentFromTemplate noShow(AppointmentFromTemplateNoShowDTO dto) {
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
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Cannot confirm appointment without patient");
        }
        appointment.setConfirmedAt(Instant.now());
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        return appointmentFromTemplateRepository.save(appointment);
    }

    @Transactional
    public AppointmentFromTemplate checkIn(Long id) {
        AppointmentFromTemplate appointment = getAppointment(id);

        validateCheckInable(appointment);

        if (appointment.getPatient() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Cannot check in appointment without patient");
        }

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setCheckedInAt(Instant.now());

        AppointmentFromTemplate savedAppointment = appointmentFromTemplateRepository.save(appointment);

        DepartmentDTO department = departmentHelper.getDepartment(savedAppointment.getDepartmentId());

        PatientEncounter encounter = createEncounter(savedAppointment, department);

        if (savedAppointment.getResourceType() == TemplateType.DIAGNOSTIC_TEST) {
            encounter = patientEncounterService.startEncounter(encounter.getId());
            createAndSubmitDiagnosticOrderFlow(savedAppointment, encounter);
        }
        else if (savedAppointment.getResourceType() == TemplateType.CATALOG){
            encounter = patientEncounterService.startEncounter(encounter.getId());
            createAndSubmitCatalogOrderFlow(savedAppointment, encounter);
        }

        return savedAppointment;
    }

    public AppointmentFromTemplateQuickAppointmentResponseVM createQuickAppointment(AppointmentFromTemplateQuickAppointmentDTO appointmentDTO) {
        LOG.info("[CREATE QUICK APPOINTMENT] facilityId={}, departmentId={}, resourceType={}, resourceId={}, patientId={}",
                appointmentDTO.facilityId(),
                appointmentDTO.departmentId(),
                appointmentDTO.resourceType(),
                appointmentDTO.resourceId(),
                appointmentDTO.patientId());

        DepartmentDTO department = departmentHelper.getDepartment(appointmentDTO.departmentId());

        if (department.defaultDurationMinutes() == null || department.defaultDurationMinutes() <= 0) {
            throw new BadRequestAlertException(
                    "defaultdurationinvalid",
                    "department",
                    "Department default duration is invalid"
            );
        }

        Patient patient = patientRepository.findById(appointmentDTO.patientId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found",
                        "patient",
                        "idnotfound"
                ));


        Instant startDateTime = Instant.now();
        Instant endDateTime = startDateTime.plusSeconds(department.defaultDurationMinutes() * 60L);

        AppointmentFromTemplate appointment = new AppointmentFromTemplate();
        appointment.setFacilityId(appointmentDTO.facilityId());
        appointment.setDepartmentId(appointmentDTO.departmentId());
        appointment.setResourceType(appointmentDTO.resourceType());
        appointment.setResourceId(appointmentDTO.resourceId());
        appointment.setPatient(patient);
        appointment.setStartDatetime(startDateTime);
        appointment.setEndDatetime(endDateTime);
        appointment.setDefaultServiceId(appointmentDTO.defaultServiceId());
        appointment.setDefaultPractitionerId(appointmentDTO.defaultPractitionerId());
        appointment.setBookingMode(BookingMode.QUICK);
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setPriority(appointmentDTO.priority());
        appointment.setOriginType(appointmentDTO.originType());
        appointment.setOriginName(appointmentDTO.originName());
        appointment.setReason(appointmentDTO.reason());
        appointment.setNote(appointmentDTO.note());
        appointment.setService(appointmentDTO.service());
        if (appointmentDTO.service() == EncounterReason.FOLLOW_UP && appointmentDTO.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(appointmentDTO.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException("Patient Encounter not found with id: " + appointmentDTO.followUpEncounterId(), ENTITY_NAME, "notfound"));

            appointment.setFollowUpEncounter(followUpEncounter);
        }
        appointment.setCapacityIndex(1);
        AppointmentFromTemplate quickAppointment = appointmentFromTemplateRepository.save(appointment);
        PatientEncounter encounter = createEncounter(quickAppointment, department);

        return new AppointmentFromTemplateQuickAppointmentResponseVM(quickAppointment, encounter);
    }

    public Page<AppointmentFromTemplate> getAppointmentByAvailabilityGenerationBatch(Long availabilityGenerationId, Pageable pageable) {
        LOG.debug("Request to get appointments for availability generation batch availabilityGenerationBatchId={}", availabilityGenerationId);

        AvailabilityGenerationBatch batch = getBatch(availabilityGenerationId);

        return appointmentFromTemplateRepository.findByAvailabilityGenerationBatch_Id(batch.getId(), pageable);
    }

    public Page<AppointmentFromTemplate> getAppointmentsByDepartmentBetweenDates(Long departmentId, Instant startDatetime, Instant endDatetime, Pageable pageable) {
        LOG.debug("Request to get appointments for the department between startDatetime={} and endDatetime={}", startDatetime, endDatetime);


        return appointmentFromTemplateRepository.findByDepartmentIdAndStartDatetimeBetween(departmentId, startDatetime, endDatetime, pageable);
    }

    public AppointmentFromTemplate getById(Long appointmentId) {
        LOG.debug("[GET_BY_ID] appointmentId={}", appointmentId);

        return appointmentFromTemplateRepository.findById(appointmentId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] appointment not found id={}", appointmentId);
                    return new BadRequestAlertException(
                            "appointment not found with id " + appointmentId,
                            "appointment",
                            "id.notfound"
                    );
                });
    }

    private void createAndSubmitDiagnosticOrderFlow(AppointmentFromTemplate appointment, PatientEncounter encounter) {
        if (appointment.getResourceId() == null) {
            throw new BadRequestAlertException("resourceidrequired", ENTITY_NAME, "Diagnostic test appointment must have resourceId");
        }

        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Diagnostic test appointment must have patient");
        }

        DiagnosticTestSetupDTO diagnosticTest = diagnosticTestHelper.getDiagnosticTest(appointment.getResourceId());

        if (diagnosticTest == null) {
            throw new BadRequestAlertException("Diagnostic test not found with id: " + appointment.getResourceId(), "DiagnosticTest", "notfound");
        }

        if (Boolean.FALSE.equals(diagnosticTest.isActive())) {
            throw new BadRequestAlertException("inactive", "DiagnosticTest", "Diagnostic test is inactive");
        }

        DiagnosticOrderCreateDTO orderCreateDTO = new DiagnosticOrderCreateDTO(
                appointment.getPatient().getId(),
                encounter.getId(),
                false,
                resolveLabStatus(diagnosticTest.type()),
                resolveRadStatus(diagnosticTest.type()),
                appointment.getDepartmentId(),
                appointment.getFacilityId()
        );

        DiagnosticOrder diagnosticOrder = diagnosticOrderService.create(orderCreateDTO);

        DiagnosticOrderTestCreateDTO orderTestCreateDTO = new DiagnosticOrderTestCreateDTO(
                diagnosticOrder.getId(),
                diagnosticTest.id(),
                appointment.getDepartmentId(),
                appointment.getReason(),
                appointment.getNote(),
                diagnosticTest.type(),
                null
        );

        diagnosticOrderTestService.create(orderTestCreateDTO);

        diagnosticOrderService.submit(diagnosticOrder, currentUsername());
    }

    private void createAndSubmitCatalogOrderFlow(AppointmentFromTemplate appointment, PatientEncounter encounter) {
        if (appointment.getResourceId() == null) {
            throw new BadRequestAlertException("resourceidrequired", ENTITY_NAME, "Catalog appointment must have resourceId");
        }

        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Catalog appointment must have patient");
        }

        List<DiagnosticTestSetupDTO> diagnosticTests = catalogHelper.getTestsByCatalog(appointment.getResourceId());

        if (diagnosticTests == null || diagnosticTests.isEmpty()) {
            throw new BadRequestAlertException("empty", "Catalog", "Catalog does not contain diagnostic tests");
        }

        boolean hasLab = diagnosticTests.stream()
                .anyMatch(test -> test.type() == TestType.LABORATORY);

        boolean hasRadiology = diagnosticTests.stream()
                .anyMatch(test -> test.type() == TestType.RADIOLOGY);

        DiagnosticOrderCreateDTO orderCreateDTO = new DiagnosticOrderCreateDTO(
                appointment.getPatient().getId(),
                encounter.getId(),
                false,
                hasLab ? DiagnosticStatus.NEW : null,
                hasRadiology ? DiagnosticStatus.NEW : null,
                appointment.getDepartmentId(),
                appointment.getFacilityId()
        );

        DiagnosticOrder diagnosticOrder = diagnosticOrderService.create(orderCreateDTO);

        for (DiagnosticTestSetupDTO diagnosticTest : diagnosticTests) {
            if (diagnosticTest == null) {
                continue;
            }

            if (Boolean.FALSE.equals(diagnosticTest.isActive())) {
                throw new BadRequestAlertException(
                        "inactive",
                        "DiagnosticTest",
                        "Diagnostic test is inactive: " + diagnosticTest.id()
                );
            }

            DiagnosticOrderTestCreateDTO orderTestCreateDTO = new DiagnosticOrderTestCreateDTO(
                    diagnosticOrder.getId(),
                    diagnosticTest.id(),
                    appointment.getDepartmentId(),
                    appointment.getReason(),
                    appointment.getNote(),
                    diagnosticTest.type(),
                    null
            );

            diagnosticOrderTestService.create(orderTestCreateDTO);
        }

        diagnosticOrderService.submit(diagnosticOrder, currentUsername());
    }

    private DiagnosticStatus resolveLabStatus(TestType type) {
        if (type == null) {
            return DiagnosticStatus.NEW;
        }

        return switch (type) {
            case LABORATORY, PATHOLOGY, MICROBIOLOGY -> DiagnosticStatus.NEW;
            case RADIOLOGY -> null;
        };
    }

    private DiagnosticStatus resolveRadStatus(TestType type) {
        if (type == null) {
            return DiagnosticStatus.NEW;
        }

        return switch (type) {
            case RADIOLOGY -> DiagnosticStatus.NEW;
            case LABORATORY, PATHOLOGY, MICROBIOLOGY -> null;
        };
    }

    private AvailabilityGenerationBatch getBatch(Long id) {
        return availabilityGenerationBatchRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Batch not found: " + id, ENTITY_NAME, "notfound"));
    }

    private PatientEncounter createEncounter(AppointmentFromTemplate savedAppointment, DepartmentDTO department) {
        PatientEncounterCreateDTO encounterCreateDTO = new PatientEncounterCreateDTO(
                savedAppointment.getPatient() != null ? savedAppointment.getPatient().getId() : null,
                savedAppointment.getFacilityId(),
                savedAppointment.getDepartmentId(),
                savedAppointment.getDefaultPractitionerId(),
                savedAppointment.getId(),
                department.encounterType(),
                savedAppointment.getService(),
                savedAppointment.getFollowUpEncounter() != null ? savedAppointment.getFollowUpEncounter().getId() : null,
                savedAppointment.getPriority(),
                savedAppointment.getOriginType(),
                savedAppointment.getOriginName(),
                savedAppointment.getNote(),
                savedAppointment.getStartDatetime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate(),
                EncounterStatus.NEW,
                savedAppointment.getReason()

        );

        return patientEncounterService.create(encounterCreateDTO);
    }

    private AppointmentFromTemplate getAppointment(Long id) {
        return appointmentFromTemplateRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Appointment not found: " + id, ENTITY_NAME, "notfound"));
    }

    private void validateCancelable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("alreadycancelled", ENTITY_NAME, "Appointment already cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Checked-in appointment cannot be cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "IN_SERVICE appointment cannot be cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Completed appointment cannot be cancelled");
        }

    }

    private void validateNoShowable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Cancelled appointment cannot be marked as no-show");
        }
        if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Checked-in appointment cannot be marked as no-show");
        } else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "IN_SERVICE appointment cannot be No-show");
        } else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Completed appointment cannot be No-show");
        }
    }

    private void validateConfirmable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only booked appointments can be confirmed");
        }
    }

    private void validateCheckInable(AppointmentFromTemplate appointment) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only confirmed appointments can be checked in");
        }
        if (appointment.getStartDatetime() == null) {
            throw new BadRequestAlertException(
                    "Appointment start date is missing",
                    ENTITY_NAME,
                    "startdatetimerequired"


                    );
        }

        LocalDate appointmentDate = appointment.getStartDatetime()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        if (!today.equals(appointmentDate)) {
            throw new BadRequestAlertException(
                    "invalidcheckindate",
                    ENTITY_NAME,
                    "Check-in is allowed only on the appointment date"
            );
        }
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("No authenticated user", ENTITY_NAME, "unauthenticated"));
    }

}
