package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
public class AppointmentAutoNoShowScheduler {

    private static final List<AppointmentStatus> ELIGIBLE_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CONFIRMED
    );

    @Value("${patient.appointment.auto-no-show.job-zone}")
    private String jobZone;

    @Value("${patient.appointment.auto-no-show.system-job-user}")
    private String systemJobUser;

    @Value("${patient.appointment.auto-no-show.no-show-reason}")
    private String noShowReason;

    private final AppointmentRepository appointmentRepository;

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void markPastAppointmentsAsNoShow() {
        ZoneId zone = ZoneId.of(jobZone);
        LocalDate today = LocalDate.now(zone);
        Instant cutoff = today.atStartOfDay(zone).toInstant();

        List<com.dazzle.asklepios.domain.Appointment> appointments =
                appointmentRepository.findByStatusInAndStartDatetimeBefore(ELIGIBLE_STATUSES, cutoff);

        if (appointments.isEmpty()) {
            log.info("Appointment no-show scheduler ran for {} and found no eligible appointments", today);
            return;
        }

        Instant now = Instant.now();
        List<com.dazzle.asklepios.domain.Appointment> updatedAppointments = new ArrayList<>(appointments.size());
        for (com.dazzle.asklepios.domain.Appointment appointment : appointments) {
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointment.setNoShowReason(noShowReason);
            appointment.setLastModifiedBy(systemJobUser);
            appointment.setLastModifiedDate(now);
            updatedAppointments.add(appointment);
        }

        appointmentRepository.saveAll(updatedAppointments);
        log.info(
                "Appointment no-show scheduler marked {} past appointments as NO_SHOW for dates before {}",
                updatedAppointments.size(),
                today
        );
    }
}