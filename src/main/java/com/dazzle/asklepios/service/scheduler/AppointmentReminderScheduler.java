package com.dazzle.asklepios.service.scheduler;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AppointmentReminderScheduler {

    private static final int BATCH_LIMIT = 100;
    private static final int MAX_BATCHES = 20;
    private static final List<AppointmentStatus> ELIGIBLE_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CONFIRMED
    );
    private static final DateTimeFormatter APPOINTMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final DepartmentHelper departmentHelper;
    private final PractitionerHelper practitionerHelper;
    private final NotificationHelper notificationHelper;
    private final TransactionTemplate transactionTemplate;

    @Value("${patient.appointment.scheduling.zone}")
    private String jobZone;

    public AppointmentReminderScheduler(
            AppointmentRepository appointmentRepository,
            DepartmentHelper departmentHelper,
            PractitionerHelper practitionerHelper,
            NotificationHelper notificationHelper,
            PlatformTransactionManager transactionManager
    ) {
        this.appointmentRepository = appointmentRepository;
        this.departmentHelper = departmentHelper;
        this.practitionerHelper = practitionerHelper;
        this.notificationHelper = notificationHelper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
            cron = "${patient.appointment.reminder.cron:0 0 * * * *}",
            zone = "${patient.appointment.scheduling.zone}"
    )
    public void remindPatientsBeforeAppointmentDay() {
        ZoneId zone = ZoneId.of(jobZone);
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        Instant windowStart = tomorrow.atStartOfDay(zone).toInstant();
        Instant windowEnd = tomorrow.plusDays(1).atStartOfDay(zone).toInstant();

        int reminded = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Appointment> dueAppointments = appointmentRepository
                    .findByPatientIsNotNullAndStatusInAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThanAndReminderNotificationSentFalseOrderByStartDatetimeAsc(
                            ELIGIBLE_STATUSES,
                            windowStart,
                            windowEnd,
                            PageRequest.of(0, BATCH_LIMIT)
                    );

            if (dueAppointments.isEmpty()) {
                break;
            }

            int sentThisBatch = 0;
            for (Appointment appointment : dueAppointments) {
                if (remind(appointment, zone)) {
                    sentThisBatch++;
                }
            }

            reminded += sentThisBatch;
            if (sentThisBatch == 0) {
                break;
            }
        }

        if (reminded > 0) {
            log.info(
                    "[APPOINTMENT_REMINDER] sent {} patient reminder(s) for appointments on {}",
                    reminded,
                    tomorrow
            );
        }
    }

    private boolean remind(Appointment appointment, ZoneId zone) {
        if (appointment == null || appointment.getId() == null || appointment.getPatient() == null) {
            return false;
        }

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule;
        Map<String, Object> data;
        try {
            recipientsByRule = notificationHelper.recipientsForScheduledPatient(appointment.getPatient());
            if (recipientsByRule == null || recipientsByRule.isEmpty()) {
                log.warn(
                        "[APPOINTMENT_REMINDER] skip because the patient has no contact. appointmentId={}, patientId={}",
                        appointment.getId(),
                        appointment.getPatient().getId()
                );
                return false;
            }
            data = buildReminderData(appointment, zone);
        } catch (Exception e) {
            log.warn(
                    "[APPOINTMENT_REMINDER] skip because reminder data could not be built. appointmentId={}, error={}",
                    appointment.getId(),
                    e.getMessage()
            );
            return false;
        }

        Instant sentAt = Instant.now();
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Appointment current = appointmentRepository.findById(appointment.getId()).orElse(null);
            if (current == null
                    || Boolean.TRUE.equals(current.getReminderNotificationSent())
                    || !ELIGIBLE_STATUSES.contains(current.getStatus())) {
                return false;
            }
            current.setReminderNotificationSent(true);
            current.setReminderNotificationSentAt(sentAt);
            appointmentRepository.save(current);
            return true;
        }));
        if (!claimed) {
            return false;
        }

        boolean sent = notificationHelper.sendNotification(
                appointment.getFacilityId(),
                NotificationCode.APPOINTMENT_REMINDER,
                recipientsByRule,
                data,
                "APPOINTMENT",
                appointment.getId()
        );

        if (!sent) {
            transactionTemplate.executeWithoutResult(status ->
                    appointmentRepository.findById(appointment.getId()).ifPresent(current -> {
                        if (sentAt.equals(current.getReminderNotificationSentAt())) {
                            current.setReminderNotificationSent(false);
                            current.setReminderNotificationSentAt(null);
                            appointmentRepository.save(current);
                        }
                    })
            );
            log.warn(
                    "[APPOINTMENT_REMINDER] notification was not created. appointmentId={}",
                    appointment.getId()
            );
            return false;
        }

        log.debug("[APPOINTMENT_REMINDER] notification created. appointmentId={}", appointment.getId());
        return true;
    }

    private Map<String, Object> buildReminderData(Appointment appointment, ZoneId zone) {
        DepartmentDTO department = null;
        if (appointment.getDepartmentId() != null) {
            try {
                department = departmentHelper.getDepartmentInternal(appointment.getDepartmentId());
            } catch (Exception e) {
                log.warn(
                        "[APPOINTMENT_REMINDER] department lookup failed. appointmentId={}, departmentId={}, error={}",
                        appointment.getId(),
                        appointment.getDepartmentId(),
                        e.getMessage()
                );
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facility_name", "");
        data.put("appointment_id", appointment.getId());
        data.put("appointment_number", appointment.getId());
        data.put("department_id", appointment.getDepartmentId());
        data.put("department_name", department != null && department.name() != null ? department.name() : "");
        data.put("patient_name", notificationHelper.getPatientName(appointment.getPatient()));
        data.put("practitioner_name", practitionerName(appointment));
        data.put(
                "appointment_date",
                appointment.getStartDatetime() != null
                        ? APPOINTMENT_DATE_FORMAT.format(appointment.getStartDatetime().atZone(zone))
                        : ""
        );
        return data;
    }

    private String practitionerName(Appointment appointment) {
        if (appointment.getDefaultPractitionerId() == null) {
            return "";
        }

        try {
            PractitionerDTO practitioner = practitionerHelper.getPractitionerInternal(appointment.getDefaultPractitionerId());
            if (practitioner == null) {
                return "";
            }
            String firstName = practitioner.firstName() != null ? practitioner.firstName() : "";
            String lastName = practitioner.lastName() != null ? practitioner.lastName() : "";
            return (firstName + " " + lastName).trim();
        } catch (Exception e) {
            log.warn(
                    "[APPOINTMENT_REMINDER] practitioner lookup failed. appointmentId={}, practitionerId={}, error={}",
                    appointment.getId(),
                    appointment.getDefaultPractitionerId(),
                    e.getMessage()
            );
            return "";
        }
    }
}
