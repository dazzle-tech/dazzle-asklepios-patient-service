package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.domain.ActivityTemplate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.JobRole;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.ActivityTemplateRepository;
import com.dazzle.asklepios.repository.PatientActivityRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientActivity.TaskAssignedEvent;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tells staff about their tasks: right away when one is assigned to them, and in a morning digest
 * of what is due today or overdue. Unassigned tasks go into the digest of everyone in their team.
 * Channels and wording come from the TASK_ASSIGNED and TASK_DAILY_DIGEST notification templates,
 * with the TASK_USER recipient rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskNotificationService {

    public static final String RULE_TASK_USER = "TASK_USER";
    private static final String ENTITY_TYPE = "PATIENT_ACTIVITY";
    private static final int DIGEST_LINES = 10;
    private static final Set<String> JOB_ROLES = Arrays.stream(JobRole.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final NotificationHelper notificationHelper;
    private final PatientActivityRepository patientActivityRepository;
    private final PatientRepository patientRepository;
    private final ActivityTemplateRepository activityTemplateRepository;
    private final UserRepository userRepository;

    @Value("${patient.tasks.daily-digest.zone:Asia/Gaza}")
    private String digestZone;

    @TransactionalEventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        try {
            if (event.assignee() == null || event.assignee().equals(event.assignedBy())) {
                return;
            }
            PatientActivity task = patientActivityRepository.findById(event.taskId()).orElse(null);
            User user = userRepository.findByLogin(event.assignee()).orElse(null);
            if (task == null || user == null || !user.isActivated()) {
                return;
            }

            Patient patient = patientRepository.findById(task.getPatientId()).orElse(null);
            Map<String, Object> data = new HashMap<>(taskData(task, patient, templateNames()));
            data.put("assigned_by", event.assignedBy());

            notificationHelper.sendNotification(
                    task.getFacilityId() != null
                            ? task.getFacilityId()
                            : SecurityUtils.getCurrentUserFacility().orElse(null),
                    NotificationCode.TASK_ASSIGNED,
                    Map.of(RULE_TASK_USER, List.of(recipient(user))),
                    data,
                    ENTITY_TYPE,
                    task.getId()
            );
        } catch (Exception e) {
            log.warn("[TASK_NOTIFICATION] assigned notice failed taskId={} error={}", event.taskId(), e.getMessage());
        }
    }

    @Scheduled(
            cron = "${patient.tasks.daily-digest.cron:0 0 7 * * *}",
            zone = "${patient.tasks.daily-digest.zone:Asia/Gaza}"
    )
    @Transactional(readOnly = true)
    public void sendDailyDigest() {
        ZoneId zone = ZoneId.of(digestZone);
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();

        List<PatientActivity> tasks = patientActivityRepository
                .findByStatusAndDueAtBeforeOrderByDueAtAsc(PatientActivityStatus.OPEN, startOfTomorrow);
        if (tasks.isEmpty()) {
            log.info("[TASK_DIGEST] nothing due today");
            return;
        }

        Map<String, List<PatientActivity>> tasksByLogin = groupByRecipient(tasks);
        Map<String, User> users = userRepository.findByLoginIn(tasksByLogin.keySet()).stream()
                .filter(User::isActivated)
                .collect(Collectors.toMap(User::getLogin, Function.identity(), (a, b) -> a));
        Map<Long, Patient> patients = patientRepository.findAllById(
                tasks.stream().map(PatientActivity::getPatientId).distinct().toList()
        ).stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<String, String> names = templateNames();

        int sent = 0;
        for (Map.Entry<String, List<PatientActivity>> entry : tasksByLogin.entrySet()) {
            User user = users.get(entry.getKey());
            if (user == null) {
                continue;
            }
            List<PatientActivity> userTasks = entry.getValue();
            long overdue = userTasks.stream().filter(task -> task.getDueAt().isBefore(startOfToday)).count();

            Map<String, Object> data = new HashMap<>();
            data.put("task_count", userTasks.size());
            data.put("overdue_count", overdue);
            data.put("due_today_count", userTasks.size() - overdue);
            data.put("task_lines", digestLines(userTasks, patients, names, zone, startOfToday));

            try {
                notificationHelper.sendNotification(
                        null,
                        NotificationCode.TASK_DAILY_DIGEST,
                        Map.of(RULE_TASK_USER, List.of(recipient(user))),
                        data,
                        ENTITY_TYPE,
                        null
                );
                sent++;
            } catch (Exception e) {
                log.warn("[TASK_DIGEST] failed login={} error={}", user.getLogin(), e.getMessage());
            }
        }
        log.info("[TASK_DIGEST] tasks={} recipients={}", tasks.size(), sent);
    }

    /**
     * Assigned tasks go to their assignee; unassigned ones to the active users of their team who
     * work in the task's facility. Tasks from before facilities were recorded go to the whole team.
     */
    private Map<String, List<PatientActivity>> groupByRecipient(List<PatientActivity> tasks) {
        Map<String, List<String>> teamLogins = new HashMap<>();
        Map<String, List<PatientActivity>> result = new LinkedHashMap<>();
        for (PatientActivity task : tasks) {
            Stream<String> logins = task.getAssignedTo() != null
                    ? Stream.of(task.getAssignedTo())
                    : task.getAssignedRoles().stream()
                            .filter(JOB_ROLES::contains)
                            .flatMap(role -> teamLogins
                                    .computeIfAbsent(task.getFacilityId() + "|" + role, key -> teamOf(task.getFacilityId(), role))
                                    .stream());
            logins.distinct().forEach(login -> result.computeIfAbsent(login, key -> new ArrayList<>()).add(task));
        }
        return result;
    }

    private List<String> teamOf(Long facilityId, String role) {
        if (facilityId != null) {
            return userRepository.findActiveLoginsByFacilityAndJobRoles(facilityId, List.of(role));
        }
        return userRepository.findByActivatedTrueAndJobRoleIn(List.of(JobRole.valueOf(role))).stream()
                .map(User::getLogin)
                .toList();
    }

    private String digestLines(
            List<PatientActivity> tasks,
            Map<Long, Patient> patients,
            Map<String, String> names,
            ZoneId zone,
            Instant startOfToday
    ) {
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        DateTimeFormatter day = DateTimeFormatter.ofPattern("dd MMM").withZone(zone);
        List<String> lines = tasks.stream()
                .sorted(Comparator.comparing(PatientActivity::getDueAt))
                .limit(DIGEST_LINES)
                .map(task -> {
                    boolean overdue = task.getDueAt().isBefore(startOfToday);
                    String when = overdue ? "Overdue since " + day.format(task.getDueAt()) : time.format(task.getDueAt());
                    return "• " + when + " — " + names.getOrDefault(task.getActivityType(), task.getActivityType())
                            + " — " + patientName(patients.get(task.getPatientId()));
                })
                .collect(Collectors.toCollection(ArrayList::new));
        if (tasks.size() > DIGEST_LINES) {
            lines.add("… and " + (tasks.size() - DIGEST_LINES) + " more");
        }
        return String.join("\n", lines);
    }

    private Map<String, Object> taskData(PatientActivity task, Patient patient, Map<String, String> names) {
        ZoneId zone = ZoneId.of(digestZone);
        Map<String, Object> data = new HashMap<>();
        data.put("task_id", task.getId());
        data.put("task_name", names.getOrDefault(task.getActivityType(), task.getActivityType()));
        data.put("task_note", Objects.toString(task.getNote(), ""));
        data.put("task_priority", task.getPriority() == null ? "" : task.getPriority().name());
        data.put("task_due_at", task.getDueAt() == null
                ? ""
                : DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(zone).format(task.getDueAt()));
        data.put("patient_name", patientName(patient));
        data.put("patient_mrn", patient == null ? "" : Objects.toString(patient.getMedicalRecordNumber(), ""));
        return data;
    }

    private NotificationResolvedRecipientDTO recipient(User user) {
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();
        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(user.getId())
                .recipientName(name.isEmpty() ? user.getLogin() : name)
                .recipientEmail(user.getEmail())
                .toEmails(hasEmail ? List.of(user.getEmail()) : List.of())
                .toPhone(user.getPhoneNumber())
                .language(user.getLangKey() != null && !user.getLangKey().isBlank() ? user.getLangKey() : "en")
                .build();
    }

    private Map<String, String> templateNames() {
        return activityTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(ActivityTemplate::getCode, ActivityTemplate::getName, (a, b) -> a));
    }

    private String patientName(Patient patient) {
        if (patient == null) {
            return "";
        }
        return Stream.of(patient.getFirstName(), patient.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }
}
