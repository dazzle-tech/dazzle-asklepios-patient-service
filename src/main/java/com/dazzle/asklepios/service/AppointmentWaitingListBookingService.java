package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentBookingGroup;
import com.dazzle.asklepios.domain.AppointmentWaitingList;
import com.dazzle.asklepios.domain.AppointmentWaitingListBooking;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.AppointmentBookingGroupSourceType;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.WaitingListStatus;
import com.dazzle.asklepios.repository.AppointmentBookingGroupRepository;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentWaitingListBookingRepository;
import com.dazzle.asklepios.repository.AppointmentWaitingListRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListBookDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.AppointmentWaitingListVM;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentWaitingListBookingService {

    private final AppointmentWaitingListRepository waitingListRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentBookingGroupRepository bookingGroupRepository;
    private final AppointmentWaitingListBookingRepository waitingListBookingRepository;
    private final PatientRepository patientRepository;

    public AppointmentWaitingListVM book(Long waitingListId, AppointmentWaitingListBookDTO dto) {
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

        List<Appointment> appointments = appointmentRepository.findAllByIdInForUpdate(dto.appointmentIds());

        if (appointments.size() != dto.appointmentIds().size()) {
            throw new BadRequestAlertException(
                    "Some appointments were not found",
                    "appointmentWaitingList",
                    "appointmentnotfound"
            );
        }

        appointments = appointments
                .stream()
                .sorted(Comparator.comparing(Appointment::getStartDatetime))
                .toList();

        validateAppointments(waitingList, appointments);

        Appointment first = appointments.get(0);
        Appointment last = appointments.get(appointments.size() - 1);

        int totalDuration = appointments
                .stream()
                .mapToInt(this::getMinutes)
                .sum();

        AppointmentBookingGroup bookingGroup = new AppointmentBookingGroup();
        bookingGroup.setFacilityId(waitingList.getFacilityId());
        bookingGroup.setPatientId(waitingList.getPatient().getId());
        bookingGroup.setDepartmentId(waitingList.getDepartmentId());
        bookingGroup.setServiceId(waitingList.getServiceId());
        bookingGroup.setPractitionerId(waitingList.getPractitionerId());
        bookingGroup.setSourceType(AppointmentBookingGroupSourceType.WAITING_LIST);
        bookingGroup.setTotalDurationMinutes(totalDuration);
        bookingGroup.setStartDatetime(first.getStartDatetime());
        bookingGroup.setEndDatetime(last.getEndDatetime());
        bookingGroup.setNotes(dto.notes());

        bookingGroup = bookingGroupRepository.save(bookingGroup);

        int sequence = 1;
        Patient patient = getPatient(waitingList.getPatient().getId());
        for (Appointment appointment : appointments) {
            AppointmentStatus oldStatus = appointment.getStatus();
            BookingMode oldBookingMode = appointment.getBookingMode();

            appointment.setStatus(AppointmentStatus.BOOKED);
            appointment.setPatient(patient);
            appointment.setBookingGroup(bookingGroup);
            appointment.setWaitingList(waitingList);

            if (waitingList.getServiceId() != null) {
                appointment.setDefaultServiceId(waitingList.getServiceId());
            }

            if (waitingList.getPractitionerId() != null) {
                appointment.setDefaultPractitionerId(waitingList.getPractitionerId());
            }

            appointmentRepository.save(appointment);

            AppointmentWaitingListBooking detail = new AppointmentWaitingListBooking();
            detail.setWaitingList(waitingList);
            detail.setBookingGroup(bookingGroup);
            detail.setAppointment(appointment);
            detail.setSequenceNo(sequence++);
            detail.setStartDatetime(appointment.getStartDatetime());
            detail.setEndDatetime(appointment.getEndDatetime());
            detail.setSourceStatusBeforeBooking(oldStatus);
            detail.setBookingMode(oldBookingMode);

            waitingListBookingRepository.save(detail);
        }

        waitingList.setStatus(WaitingListStatus.BOOKED);
        waitingList.setBookingGroup(bookingGroup);
        waitingList.setBookedAt(Instant.now());

        waitingList = waitingListRepository.save(waitingList);

        return toVm(waitingList);
    }

    private Patient getPatient(Long id) {
        return patientRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "AppointmentWaitingListBooking", "notfound"));
    }

    private void validateAppointments(AppointmentWaitingList waitingList, List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one appointment is required",
                    "appointmentWaitingList",
                    "appointmentrequired"
            );
        }

        List<AppointmentStatus> allowedStatuses = List.of(
                AppointmentStatus.NEW,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED
        );

        List<BookingMode> allowedModes = List.of(
                BookingMode.SLOT,
                BookingMode.BUFFER
        );


        for (int i = 0; i < appointments.size(); i++) {
            Appointment appointment = appointments.get(i);

            if (!allowedStatuses.contains(appointment.getStatus())) {
                throw new BadRequestAlertException(
                        "Appointment status cannot be booked from waiting list",
                        "appointmentWaitingList",
                        "invalidappointmentstatus"
                );
            }

            if (!allowedModes.contains(appointment.getBookingMode())) {
                throw new BadRequestAlertException(
                        "Appointment booking mode is not allowed",
                        "appointmentWaitingList",
                        "invalidbookingmode"
                );
            }

            if (appointment.getBookingGroup() != null || appointment.getWaitingList() != null) {
                throw new BadRequestAlertException(
                        "Appointment is already linked to another booking",
                        "appointmentWaitingList",
                        "alreadylinked"
                );
            }

            if (!appointment.getFacilityId().equals(waitingList.getFacilityId())) {
                throw new BadRequestAlertException(
                        "Appointment facility mismatch",
                        "appointmentWaitingList",
                        "facilitymismatch"
                );
            }

            if (!appointment.getDepartmentId().equals(waitingList.getDepartmentId())) {
                throw new BadRequestAlertException(
                        "Appointment department mismatch",
                        "appointmentWaitingList",
                        "departmentmismatch"
                );
            }

            if (waitingList.getPractitionerId() != null && appointment.getDefaultPractitionerId() != null) {
                if (!waitingList.getPractitionerId().equals(appointment.getDefaultPractitionerId())) {
                    throw new BadRequestAlertException(
                            "Appointment practitioner mismatch",
                            "appointmentWaitingList",
                            "practitionermismatch"
                    );
                }
            }

            if (i > 0) {
                Appointment previous = appointments.get(i - 1);

                if (!appointment.getStartDatetime().equals(previous.getEndDatetime())) {
                    throw new BadRequestAlertException(
                            "Selected appointments must be sequential",
                            "appointmentWaitingList",
                            "notsequential"
                    );
                }
            }
        }
    }

    private int getMinutes(Appointment appointment) {
        return Math.toIntExact(
                Duration.between(
                        appointment.getStartDatetime(),
                        appointment.getEndDatetime()
                ).toMinutes()
        );
    }

    private AppointmentWaitingListVM toVm(AppointmentWaitingList entity) {
        return new AppointmentWaitingListVM(
                entity.getId(),
                entity.getPatient()!=null ? entity.getPatient().getId():null,
                entity.getPatient()!=null ?getPatientName(entity.getPatient()):null,
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

}