package com.dazzle.asklepios.service.scheduler;

import com.dazzle.asklepios.client.notification.NotificationClient;
import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.UserDepartmentHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateIntervalEndedNotificationScheduler {

    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final UserDepartmentHelper userDepartmentHelper;
    private final NotificationClient notificationClient;
    private final DepartmentHelper departmentHelper;

//    @Scheduled(fixedDelayString = "${appointment.template-interval-ended.fixed-delay:60000}")
    @Transactional
    public void notifyEndedTemplateIntervals() {
        Instant now = Instant.now();

        List<AvailabilityGenerationBatch> endedBatches =
                availabilityGenerationBatchRepository
                        .findTop100ByApplyEndDateTimeLessThanEqualAndIntervalEndedNotificationSentFalseAndExecutionStatusOrderByApplyEndDateTimeAsc(
                                now,
                                BatchStatus.COMPLETED
                        );

        if (endedBatches.isEmpty()) {
            return;
        }

        log.debug("[TEMPLATE_INTERVAL_ENDED] found ended batches count={}", endedBatches.size());

        for (AvailabilityGenerationBatch batch : endedBatches) {
            try {
                notifyTemplateIntervalEnded(batch);

                batch.setIntervalEndedNotificationSent(true);
                batch.setIntervalEndedNotificationSentAt(now);

                availabilityGenerationBatchRepository.save(batch);
            } catch (Exception e) {
                log.warn(
                        "[TEMPLATE_INTERVAL_ENDED] failed to notify batchId={}, error={}",
                        batch.getId(),
                        e.getMessage()
                );
            }
        }
    }

    private void notifyTemplateIntervalEnded(AvailabilityGenerationBatch batch) {
        if (batch == null || batch.getTemplate() == null) {
            return;
        }

        AvailabilityTemplate template = batch.getTemplate();

        Long departmentId = template.getDepartmentId();
        DepartmentDTO departmentDTO= departmentHelper.getDepartment(departmentId);

        if (departmentId == null) {
            log.warn(
                    "[TEMPLATE_INTERVAL_ENDED] skip because departmentId is missing. batchId={}, templateId={}",
                    batch.getId(),
                    template.getId()
            );
            return;
        }

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildDepartmentUserRecipients(departmentId);

        if (departmentUsers.isEmpty()) {
            log.warn(
                    "[TEMPLATE_INTERVAL_ENDED] skip because no department users. batchId={}, departmentId={}",
                    batch.getId(),
                    departmentId
            );
            return;
        }

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", batch.getId());
        data.put("templateId", template.getId());
        data.put("templateName", template.getTemplateName() != null ? template.getTemplateName() : "");
        data.put("departmentId", departmentId);
        data.put("departmentName", departmentDTO.name());
        data.put("scope", batch.getScope() != null ? batch.getScope().toString() : "");
        data.put("applyStartDateTime", batch.getApplyStartDateTime() != null ? batch.getApplyStartDateTime().toString() : "");
        data.put("applyEndDateTime", batch.getApplyEndDateTime() != null ? batch.getApplyEndDateTime().toString() : "");
        data.put("totalSlots", batch.getTotalSlots() != null ? batch.getTotalSlots() : "");
        data.put("dailyAvg", batch.getDailyAvg() != null ? batch.getDailyAvg() : "");
        data.put("executionStatus", batch.getExecutionStatus() != null ? batch.getExecutionStatus().toString() : "");

        NotificationCreateDTO dto = new NotificationCreateDTO(
                template.getFacilityId(),
                NotificationCode.TEMPLATE_INTERVAL_ENDED,
                "en",
                null,
                recipientsByRule,
                data,
                "AVAILABILITY_GENERATION_BATCH",
                batch.getId()
        );

        notificationClient.createNotification(dto);

        log.debug(
                "[TEMPLATE_INTERVAL_ENDED] notification created. batchId={}, templateId={}, departmentId={}",
                batch.getId(),
                template.getId(),
                departmentId
        );
    }

    private List<NotificationResolvedRecipientDTO> buildDepartmentUserRecipients(Long departmentId) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users = userDepartmentHelper.getUsersForDepartment(departmentId);

        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of("departmentId", departmentId))
                        .build()
                )
                .toList();
    }

    private String getUserDisplayName(UserDTO user) {
        if (user == null) {
            return "";
        }

        String firstName = user.firstName() != null ? user.firstName() : "";
        String lastName = user.lastName() != null ? user.lastName() : "";

        String fullName = (firstName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (user.login() != null && !user.login().isBlank()) {
            return user.login();
        }

        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }

        return user.id() != null ? String.valueOf(user.id()) : "";
    }
}