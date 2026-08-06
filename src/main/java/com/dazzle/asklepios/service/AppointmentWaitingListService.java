package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentWaitingList;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.WaitingListPriority;
import com.dazzle.asklepios.domain.enumeration.WaitingListStatus;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentWaitingListRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListRemoveDTO;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.AppointmentWaitingListVM;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.WaitingListAvailableSlotGroupVM;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.WaitingListAvailableSlotVM;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.WaitingListAvailableSlotsByBookingModeVM;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentWaitingListService {

    private final AppointmentWaitingListRepository waitingListRepository;
    private final AvailabilityTemplateRepository availabilityTemplateRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final FacilityHelper facilityHelper;

    // fallback only - real zone is resolved per-facility, since different facilities
    // can be in different real-world time zones.
    @Value("${patient.appointment.scheduling.zone}")
    private String defaultSchedulingZone;

    private ZoneId resolveZone(Long facilityId) {
        return facilityHelper.getFacilityZoneId(facilityId, defaultSchedulingZone);
    }

    public AppointmentWaitingListVM create(AppointmentWaitingListCreateDTO dto) {

        Patient patient = getPatient(dto.patientId());

        AppointmentWaitingList waitingList = new AppointmentWaitingList();
        waitingList.setFacilityId(dto.facilityId());
        waitingList.setPatient(patient);
        waitingList.setDepartmentId(dto.departmentId());
        waitingList.setServiceId( dto.serviceId());
        waitingList.setPractitionerId( dto.practitionerId());
        waitingList.setPriority(dto.priority() != null ? dto.priority() : WaitingListPriority.NORMAL);
        waitingList.setStatus(WaitingListStatus.WAITING);
        waitingList.setPreferredDate(dto.preferredDate());
        waitingList.setExpectedDurationMinutes(dto.expectedDurationMinutes());
        waitingList.setReason(dto.reason());
        waitingList.setNotes(dto.notes());

        waitingList = waitingListRepository.save(waitingList);

        return toVm(waitingList);
    }
    public AppointmentWaitingListVM removeFromWaitingList(Long waitingListId, AppointmentWaitingListRemoveDTO dto) {
        AppointmentWaitingList waitingList = waitingListRepository.findById(waitingListId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Waiting list entry not found",
                        "appointmentWaitingList",
                        "waitinglistnotfound"
                ));

        if (waitingList.getStatus() != WaitingListStatus.WAITING) {
            throw new BadRequestAlertException(
                    "Only waiting entries can be removed",
                    "appointmentWaitingList",
                    "cannotremove"
            );
        }

        if (waitingList.getBookingGroup() != null || waitingList.getBookedAt() != null) {
            throw new BadRequestAlertException(
                    "Booked waiting list entry cannot be removed",
                    "appointmentWaitingList",
                    "alreadybooked"
            );
        }

        waitingList.setStatus(WaitingListStatus.REMOVED);

        String removeReason = dto != null ? dto.reason() : null;

        if (removeReason != null && !removeReason.isBlank()) {
            String oldNotes = waitingList.getNotes();

            if (oldNotes == null || oldNotes.isBlank()) {
                waitingList.setNotes("Removed reason: " + removeReason);
            } else {
                waitingList.setNotes(oldNotes + "\nRemoved reason: " + removeReason);
            }
        }

        waitingList = waitingListRepository.save(waitingList);

        return toVm(waitingList);
    }

    @Transactional(readOnly = true)
    public List<AppointmentWaitingListVM> getWaiting(Long facilityId, Long departmentId) {
        return waitingListRepository
                .findByFacilityIdAndDepartmentIdAndStatusOrderByCreatedDateAsc(
                        facilityId,
                        departmentId,
                        WaitingListStatus.WAITING
                )
                .stream()
                .map(this::toVm)
                .toList();
    }

    @Transactional(readOnly = true)
    public WaitingListAvailableSlotsByBookingModeVM findAvailableSlots(Long waitingListId, LocalDate preferredDate) {
        AppointmentWaitingList waitingList = waitingListRepository.findById(waitingListId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Waiting list entry not found",
                        "appointmentWaitingList",
                        "waitinglistnotfound"
                ));

        if (waitingList.getStatus() != WaitingListStatus.WAITING) {
            throw new BadRequestAlertException(
                    "Waiting list entry is not waiting",
                    "appointmentWaitingList",
                    "invalidstatus"
            );
        }

        List<AppointmentStatus> allowedStatuses = List.of(
                AppointmentStatus.NEW,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED
        );

        List<BookingMode> allowedBookingModes = List.of(
                BookingMode.SLOT,
                BookingMode.BUFFER
        );

        LocalDate searchDate = preferredDate != null
                ? preferredDate
                : waitingList.getPreferredDate();

        List<Appointment> candidates;

        if (searchDate != null) {
            ZoneId zoneId = resolveZone(waitingList.getFacilityId());

            Instant dayStart = searchDate
                    .atStartOfDay(zoneId)
                    .toInstant();

            Instant dayEnd = searchDate
                    .plusDays(1)
                    .atStartOfDay(zoneId)
                    .toInstant();

            Instant now = Instant.now();

            if (dayStart.isBefore(now)) {
                dayStart = now;
            }

            candidates =
                    appointmentRepository.findByFacilityIdAndDepartmentIdAndStartDatetimeBetweenAndStatusInAndBookingModeInOrderByStartDatetimeAsc(
                            waitingList.getFacilityId(),
                            waitingList.getDepartmentId(),
                            dayStart,
                            dayEnd,
                            allowedStatuses,
                            allowedBookingModes
                    );
        } else {
            candidates =
                    appointmentRepository.findByFacilityIdAndDepartmentIdAndStartDatetimeGreaterThanEqualAndStatusInAndBookingModeInOrderByStartDatetimeAsc(
                            waitingList.getFacilityId(),
                            waitingList.getDepartmentId(),
                            Instant.now(),
                            allowedStatuses,
                            allowedBookingModes
                    );
        }

        List<WaitingListAvailableSlotVM> availableSlots = candidates
                .stream()
                .filter(a -> isAvailableForDepartment(a, waitingList))
                .sorted(Comparator.comparing(Appointment::getStartDatetime))
                .map(this::toAvailableSlotVm)
                .toList();

        List<WaitingListAvailableSlotVM> slotAppointments = availableSlots
                .stream()
                .filter(a -> a.bookingMode() == BookingMode.SLOT)
                .toList();

        List<WaitingListAvailableSlotVM> bufferAppointments = availableSlots
                .stream()
                .filter(a -> a.bookingMode() == BookingMode.BUFFER)
                .toList();

        return new WaitingListAvailableSlotsByBookingModeVM(
                slotAppointments,
                bufferAppointments
        );
    }
    private boolean isAvailableForDepartment(Appointment appointment, AppointmentWaitingList waitingList) {
        if (appointment.getBookingGroup() != null) {
            return false;
        }

        if (appointment.getWaitingList() != null) {
            return false;
        }

        if (!appointment.getFacilityId().equals(waitingList.getFacilityId())) {
            return false;
        }

        if (!appointment.getDepartmentId().equals(waitingList.getDepartmentId())) {
            return false;
        }

        return true;
    }
    private AppointmentWaitingListVM toVm(AppointmentWaitingList entity) {
        return new AppointmentWaitingListVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getPatient() != null ? getPatientName(entity.getPatient()) : null,
                entity.getPatient() != null ? entity.getPatient().getMedicalRecordNumber() : null,
                entity.getDepartmentId(),
                entity.getServiceId(),
                entity.getPractitionerId(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getPreferredDate(),
                entity.getExpectedDurationMinutes(),
                entity.getReason(),
                entity.getNotes(),
                entity.getBookingGroup() != null ? entity.getBookingGroup().getId() : null,
                entity.getBookedAt(),
                entity.getCreatedDate()
        );
    }

    private String getPatientName(Patient patient) {
        if (patient == null) {
            return "";
        }

        String firstName = patient.getFirstName() != null ? patient.getFirstName() : "";
        String secondName = patient.getSecondName() != null ? patient.getSecondName() : "";
        String thirdName = patient.getThirdName() != null ? patient.getThirdName() : "";
        String lastName = patient.getLastName() != null ? patient.getLastName() : "";

        String fullName = (firstName + " " + secondName + " " + thirdName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
            return patient.getEmail();
        }

        return patient.getId() != null ? String.valueOf(patient.getId()) : "";
    }

    private Patient getPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "AppointmentWaitingList",
                        "patient.notfound"
                ));
    }

    private WaitingListAvailableSlotVM toAvailableSlotVm(Appointment appointment) {
        return new WaitingListAvailableSlotVM(
                appointment.getId(),
                appointment.getStartDatetime(),
                appointment.getEndDatetime(),
                appointment.getStatus(),
                appointment.getBookingMode(),
                appointment.getDefaultPractitionerId(),
                appointment.getDefaultServiceId()
        );
    }
}