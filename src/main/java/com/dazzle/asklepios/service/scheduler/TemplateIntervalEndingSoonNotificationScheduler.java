package com.dazzle.asklepios.service.scheduler;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.CatalogDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.RoomDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.helper.CatalogHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.service.helper.RoomHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateIntervalEndingSoonNotificationScheduler {

    private static final int BATCH_LIMIT = 100;

    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final DepartmentHelper departmentHelper;
    private final NotificationHelper notificationHelper;
    private final ServiceHelper serviceHelper;
    private final RoomHelper roomHelper;
    private final PractitionerHelper practitionerHelper;
    private final DiagnosticTestHelper diagnosticTestHelper;
    private final CatalogHelper catalogHelper;

    @Value("${patient.appointment.template-interval-ending-soon.zone:Asia/Gaza}")
    private String jobZone;

    @Value("${patient.appointment.template-interval-ending-soon.lead-days:7}")
    private int leadDays;

    @Scheduled(
            cron = "${patient.appointment.template-interval-ending-soon.cron:0 0 7 * * *}",
            zone = "${patient.appointment.template-interval-ending-soon.zone:Asia/Gaza}"
    )
    @Transactional
    public void notifyEndingSoonTemplateIntervals() {
        ZoneId zone = ZoneId.of(jobZone);
        Instant now = Instant.now();
        Instant dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        int daysBeforeEnd = leadDays > 0 ? leadDays : 7;
        Instant weekAhead = now.plus(daysBeforeEnd, ChronoUnit.DAYS);

        List<AvailabilityGenerationBatch> endingSoonBatches =
                availabilityGenerationBatchRepository.findEndingSoonForDailyNotification(
                        now,
                        weekAhead,
                        dayStart,
                        BatchStatus.COMPLETED,
                        PageRequest.of(0, BATCH_LIMIT)
                );

        if (endingSoonBatches.isEmpty()) {
            return;
        }

        log.debug("[TEMPLATE_INTERVAL_ENDING_SOON] found ending soon batches count={}", endingSoonBatches.size());

        for (AvailabilityGenerationBatch batch : endingSoonBatches) {
            try {
                if (hasUpcomingApply(batch)) {
                    log.debug(
                            "[TEMPLATE_INTERVAL_ENDING_SOON] skip because a later apply exists. batchId={}",
                            batch.getId()
                    );
                    continue;
                }

                if (!notifyTemplateIntervalEndingSoon(batch)) {
                    continue;
                }

                batch.setIntervalEndingSoonNotificationSentAt(now);
                availabilityGenerationBatchRepository.save(batch);
            } catch (Exception e) {
                log.warn(
                        "[TEMPLATE_INTERVAL_ENDING_SOON] failed to notify batchId={}, error={}",
                        batch.getId(),
                        e.getMessage()
                );
            }
        }
    }

    private boolean hasUpcomingApply(AvailabilityGenerationBatch batch) {
        AvailabilityTemplate template = batch.getTemplate();
        if (template == null
                || template.getDepartmentId() == null
                || template.getResourceId() == null
                || template.getTemplateType() == null
                || batch.getApplyEndDateTime() == null) {
            return false;
        }

        return availabilityGenerationBatchRepository.countLaterCompletedApplyForResource(
                batch.getId(),
                template.getDepartmentId(),
                template.getResourceId(),
                template.getTemplateType(),
                batch.getApplyEndDateTime(),
                BatchStatus.COMPLETED
        ) > 0;
    }

    private boolean notifyTemplateIntervalEndingSoon(AvailabilityGenerationBatch batch) {
        if (batch == null || batch.getTemplate() == null) {
            return false;
        }

        AvailabilityTemplate template = batch.getTemplate();
        Long departmentId = template.getDepartmentId();
        if (departmentId == null) {
            log.warn(
                    "[TEMPLATE_INTERVAL_ENDING_SOON] skip because departmentId is missing. batchId={}, templateId={}",
                    batch.getId(),
                    template.getId()
            );
            return false;
        }

        DepartmentDTO departmentDTO = departmentHelper.getDepartmentInternal(departmentId);
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        String resourceName = resolveResourceName(template);

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(
                departmentId,
                login,
                batch.getCreatedBy(),
                null,
                null,
                true
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batch_id", batch.getId());
        data.put("template_id", template.getId());
        data.put("template_name", template.getTemplateName() != null ? template.getTemplateName() : "");
        data.put("resource_id", template.getResourceId() != null ? template.getResourceId() : "");
        data.put("resource_name", resourceName);
        data.put("department_id", departmentId);
        data.put("department_name", departmentDTO != null && departmentDTO.name() != null ? departmentDTO.name() : "");
        data.put("scope", batch.getScope() != null ? batch.getScope().toString() : "");
        data.put("apply_start_date_time", batch.getApplyStartDateTime() != null ? batch.getApplyStartDateTime().toString() : "");
        data.put("apply_end_date_time", batch.getApplyEndDateTime() != null ? batch.getApplyEndDateTime().toString() : "");
        data.put("total_slots", batch.getTotalSlots() != null ? batch.getTotalSlots() : "");
        data.put("daily_avg", batch.getDailyAvg() != null ? batch.getDailyAvg() : "");
        data.put("execution_status", batch.getExecutionStatus() != null ? batch.getExecutionStatus().toString() : "");

        notificationHelper.sendNotification(
                template.getFacilityId(),
                NotificationCode.TEMPLATE_INTERVAL_ENDING_SOON,
                recipientsByRule,
                data,
                "AVAILABILITY_GENERATION_BATCH",
                batch.getId()
        );

        log.debug(
                "[TEMPLATE_INTERVAL_ENDING_SOON] notification created. batchId={}, templateId={}, departmentId={}, resourceName={}",
                batch.getId(),
                template.getId(),
                departmentId,
                resourceName
        );
        return true;
    }

    private String resolveResourceName(AvailabilityTemplate template) {
        if (template.getResourceId() == null || template.getTemplateType() == null) {
            return template.getTemplateName() != null ? template.getTemplateName() : "";
        }

        try {
            String resourceName = switch (template.getTemplateType()) {
                case SERVICE -> {
                    ServiceSetupDTO resource = serviceHelper.getServiceInternal(template.getResourceId());
                    yield resource == null ? null : resource.name();
                }
                case ROOM -> {
                    RoomDTO resource = roomHelper.getRoomInternal(template.getResourceId());
                    yield resource == null ? null : resource.name();
                }
                case PRACTITIONER -> {
                    PractitionerDTO resource = practitionerHelper.getPractitionerInternal(template.getResourceId());
                    yield resource == null ? null : practitionerName(resource);
                }
                case DIAGNOSTIC_TEST -> {
                    DiagnosticTestSetupDTO resource = diagnosticTestHelper.getDiagnosticTestInternal(template.getResourceId());
                    yield resource == null ? null : resource.name();
                }
                case CATALOG -> {
                    CatalogDTO resource = catalogHelper.getCatalogInternal(template.getResourceId());
                    yield resource == null ? null : resource.name();
                }
                case DEPARTMENT -> {
                    DepartmentDTO resource = departmentHelper.getDepartmentInternal(template.getResourceId());
                    yield resource == null ? null : resource.name();
                }
            };

            if (resourceName != null && !resourceName.isBlank()) {
                return resourceName.trim();
            }
        } catch (Exception e) {
            log.warn(
                    "[TEMPLATE_INTERVAL_ENDING_SOON] failed to resolve resource name. templateId={}, resourceId={}, error={}",
                    template.getId(),
                    template.getResourceId(),
                    e.getMessage()
            );
        }

        return template.getTemplateName() != null ? template.getTemplateName() : "";
    }

    private String practitionerName(PractitionerDTO practitioner) {
        String firstName = practitioner.firstName() != null ? practitioner.firstName() : "";
        String lastName = practitioner.lastName() != null ? practitioner.lastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
