package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final ZoneId JOB_ZONE = ZoneId.of("Asia/Hebron");
    private static final String SYSTEM_JOB_USER = "SYSTEM_JOB";
    private static final String NO_SHOW_REASON = "Not Specified";
    private static final List<AppointmentStatus> ELIGIBLE_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CONFIRMED
    );

    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void markPastAppointmentsAsNoShow() {
        LocalDate today = LocalDate.now(JOB_ZONE);
        Instant cutoff = today.atStartOfDay(JOB_ZONE).toInstant();

        List<com.dazzle.asklepios.domain.AppointmentFromTemplate> appointments =
                appointmentFromTemplateRepository.findByStatusInAndStartDatetimeBefore(ELIGIBLE_STATUSES, cutoff);

        if (appointments.isEmpty()) {
            log.info("Appointment no-show scheduler ran for {} and found no eligible appointments", today);
            return;
        }

        Instant now = Instant.now();
        List<com.dazzle.asklepios.domain.AppointmentFromTemplate> updatedAppointments = new ArrayList<>(appointments.size());
        for (com.dazzle.asklepios.domain.AppointmentFromTemplate appointment : appointments) {
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointment.setNoShowReason(NO_SHOW_REASON);
            appointment.setLastModifiedBy(SYSTEM_JOB_USER);
            appointment.setLastModifiedDate(now);
            updatedAppointments.add(appointment);
        }

        appointmentFromTemplateRepository.saveAll(updatedAppointments);
        log.info(
                "Appointment no-show scheduler marked {} past appointments as NO_SHOW for dates before {}",
                updatedAppointments.size(),
                today
        );
    }
}
