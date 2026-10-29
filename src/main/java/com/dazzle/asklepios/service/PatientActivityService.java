package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ActivityPlan;
import com.dazzle.asklepios.domain.ActivityPlanItem;
import com.dazzle.asklepios.domain.ActivityTemplate;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.PatientActivityChecklistItem;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.ActivityTemplateKind;
import com.dazzle.asklepios.domain.enumeration.PatientActivitySource;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.ActivityTemplateRepository;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientActivityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityApplyPlanDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityApplyPlanItemDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityAssignDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCancelDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityChecklistDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCompleteDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCreateDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivitySummaryVM;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientActivityService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientActivityService.class);
    private static final String ENTITY = "patientActivity";
    public static final String SOURCE_APPOINTMENT = "APPOINTMENT";
    public static final String SOURCE_ENCOUNTER = "ENCOUNTER";
    public static final String SOURCE_REFERRAL = "REFERRAL";
    public static final String SOURCE_DIAGNOSTIC_ORDER_TEST = "DIAGNOSTIC_ORDER_TEST";
    public static final String SOURCE_DIAGNOSTIC_ORDER = "DIAGNOSTIC_ORDER";
    public static final String SOURCE_CONSULTATION = "CONSULTATION";
    public static final String SOURCE_PRESCRIPTION = "PRESCRIPTION";
    public static final String SOURCE_FINANCIAL_DOCUMENT = "FINANCIAL_DOCUMENT";
    public static final String SOURCE_ELIGIBILITY_REQUEST = "ELIGIBILITY_REQUEST";
    public static final String SOURCE_PRE_AUTHORIZATION = "PRE_AUTHORIZATION";
    public static final String SOURCE_CLAIM = "CLAIM";
    public static final String SOURCE_BILLING_REFUND = "BILLING_REFUND";
    public static final String SOURCE_TELEPHONIC_CONSULTATION = "TELEPHONIC_CONSULTATION";
    public static final String SOURCE_NOTIFICATION = "NOTIFICATION";

    private final PatientActivityRepository patientActivityRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AppointmentRepository appointmentRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final ActivityTemplateRepository activityTemplateRepository;
    private final ActivitySetupService activitySetupService;
    private final UserRepository userRepository;

    public PatientActivity create(PatientActivityCreateDTO dto) {
        LOG.info("[CREATE] PatientActivity patientId={} type={}", dto.getPatientId(), dto.getActivityType());

        validatePatient(dto.getPatientId());
        ActivityTemplate template = activeTemplate(dto.getActivityType());
        validateCreatorRole(template);

        PatientActivityStatus status = dto.getStatus() != null ? dto.getStatus() : defaultStatus(template);
        validateStatusOnCreate(status);
        Instant now = Instant.now();
        Instant dueAt = dto.getDueAt();
        if (status == PatientActivityStatus.OPEN && dueAt == null) {
            dueAt = activitySetupService.resolveDueAt(template, now);
        }
        validateDueDate(status, dueAt);
        validateEncounter(dto.getPatientId(), dto.getEncounterId());
        validateAppointment(dto.getPatientId(), dto.getAppointmentId());

        String outcome = status == PatientActivityStatus.DONE ? validateOutcome(template, dto.getOutcome()) : null;

        PatientActivity activity = PatientActivity.builder()
                .patientId(dto.getPatientId())
                .encounterId(dto.getEncounterId())
                .appointmentId(dto.getAppointmentId())
                .category(template.getCategoryCode())
                .activityType(template.getCode())
                .status(status)
                .source(PatientActivitySource.STAFF)
                .priority(dto.getPriority() != null ? dto.getPriority() : template.getDefaultPriority())
                .note(noteOrFallback(dto.getNote(), outcome))
                .dueAt(dueAt)
                .assignedRoles(dto.getAssignedRoles() != null
                        ? activitySetupService.cleanRoles(dto.getAssignedRoles())
                        : new ArrayList<>(template.getAssigneeRoles()))
                .assignedTo(validateAssignee(dto.getAssignedTo()))
                .checklist(status == PatientActivityStatus.OPEN ? checklistOf(template) : new ArrayList<>())
                .build();

        if (status == PatientActivityStatus.DONE) {
            activity.setCompletedAt(now);
            activity.setCompletedBy(currentUsername());
            activity.setOutcome(outcome);
        }

        PatientActivity saved = patientActivityRepository.save(activity);
        LOG.info("[CREATE] PatientActivity id={} patientId={}", saved.getId(), saved.getPatientId());
        return saved;
    }

    /**
     * Creates the tasks of a plan for one patient. Staff may drop items or change them in the
     * preview, but every item must belong to the plan. The plan stands in for the creator-role
     * check of each template, since the setup decided who works these tasks.
     */
    public List<PatientActivity> applyPlan(PatientActivityApplyPlanDTO dto) {
        LOG.info("[APPLY_PLAN] patientId={} plan={} items={}", dto.getPatientId(), dto.getPlanCode(), dto.getItems().size());

        validatePatient(dto.getPatientId());
        ActivityPlan plan = activitySetupService.findPlanByCode(dto.getPlanCode());
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw bad("This plan is inactive.", "activityPlan.inactive");
        }
        validateEncounter(dto.getPatientId(), dto.getEncounterId());
        validateAppointment(dto.getPatientId(), dto.getAppointmentId());

        Set<String> planCodes = plan.getItems().stream()
                .map(ActivityPlanItem::getTemplateCode)
                .collect(Collectors.toSet());
        String runId = UUID.randomUUID().toString();
        List<PatientActivity> created = new ArrayList<>();

        for (PatientActivityApplyPlanItemDTO item : dto.getItems()) {
            if (!planCodes.contains(item.getActivityType())) {
                throw bad("Task is not part of this plan: " + item.getActivityType(), "activityPlan.item.notInPlan");
            }
            ActivityTemplate template = activeTemplate(item.getActivityType());
            if (template.getKind() != ActivityTemplateKind.TASK) {
                throw bad("Only tasks can be applied from a plan.", "activityPlan.item.notTask");
            }

            created.add(patientActivityRepository.save(PatientActivity.builder()
                    .patientId(dto.getPatientId())
                    .encounterId(dto.getEncounterId())
                    .appointmentId(dto.getAppointmentId())
                    .category(template.getCategoryCode())
                    .activityType(template.getCode())
                    .status(PatientActivityStatus.OPEN)
                    .source(PatientActivitySource.STAFF)
                    .priority(item.getPriority() != null ? item.getPriority() : template.getDefaultPriority())
                    .note(noteOrDefault(template, item.getNote()))
                    .dueAt(item.getDueAt())
                    .assignedRoles(item.getAssignedRoles() != null
                            ? activitySetupService.cleanRoles(item.getAssignedRoles())
                            : new ArrayList<>(template.getAssigneeRoles()))
                    .assignedTo(validateAssignee(item.getAssignedTo()))
                    .checklist(checklistOf(template))
                    .planCode(plan.getCode())
                    .planRunId(runId)
                    .build()));
        }

        LOG.info("[APPLY_PLAN] created {} tasks run={}", created.size(), runId);
        return created;
    }

    public PatientActivity update(Long id, PatientActivityUpdateDTO dto) {
        LOG.info("[UPDATE] PatientActivity id={}", id);

        PatientActivity existing = findOpenStaffActivity(id);
        boolean typeChanged = !existing.getActivityType().equals(dto.getActivityType());
        ActivityTemplate template = typeChanged
                ? activeTemplate(dto.getActivityType())
                : activitySetupService.findTemplateByCode(dto.getActivityType());
        validateDueDate(PatientActivityStatus.OPEN, dto.getDueAt());
        validateEncounter(existing.getPatientId(), dto.getEncounterId());
        validateAppointment(existing.getPatientId(), dto.getAppointmentId());

        existing.setEncounterId(dto.getEncounterId());
        existing.setAppointmentId(dto.getAppointmentId());
        existing.setCategory(template.getCategoryCode());
        existing.setActivityType(template.getCode());
        existing.setPriority(dto.getPriority() != null ? dto.getPriority() : template.getDefaultPriority());
        existing.setNote(noteOrFallback(dto.getNote(), null));
        existing.setDueAt(dto.getDueAt());
        if (dto.getAssignedRoles() != null) {
            existing.setAssignedRoles(activitySetupService.cleanRoles(dto.getAssignedRoles()));
        }
        existing.setAssignedTo(validateAssignee(dto.getAssignedTo()));
        if (typeChanged) {
            existing.setChecklist(checklistOf(template));
        }

        return patientActivityRepository.save(existing);
    }

    public PatientActivity complete(Long id, PatientActivityCompleteDTO dto) {
        LOG.info("[COMPLETE] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        ActivityTemplate template = activityTemplateRepository.findByCode(existing.getActivityType()).orElse(null);
        String outcome = validateOutcome(template, dto.getOutcome());
        String outcomeNote = trimToNull(dto.getOutcomeNote());
        if (outcome == null && outcomeNote == null) {
            throw bad("Outcome note is required.", "patientActivity.outcome.required");
        }

        existing.setStatus(PatientActivityStatus.DONE);
        existing.setCompletedAt(Instant.now());
        existing.setCompletedBy(currentUsername());
        existing.setOutcome(outcome);
        existing.setOutcomeNote(outcomeNote);
        return patientActivityRepository.save(existing);
    }

    public PatientActivity cancel(Long id, PatientActivityCancelDTO dto) {
        LOG.info("[CANCEL] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        existing.setStatus(PatientActivityStatus.CANCELLED);
        existing.setCancelledAt(Instant.now());
        existing.setCancelledBy(currentUsername());
        existing.setCancellationReason(dto.getCancellationReason().trim());
        return patientActivityRepository.save(existing);
    }

    public PatientActivity assign(Long id, PatientActivityAssignDTO dto) {
        LOG.info("[ASSIGN] PatientActivity id={} to={}", id, dto.getAssignedTo());

        PatientActivity existing = findOpenActivity(id);
        if (dto.getAssignedRoles() != null) {
            existing.setAssignedRoles(activitySetupService.cleanRoles(dto.getAssignedRoles()));
        }
        existing.setAssignedTo(validateAssignee(dto.getAssignedTo()));
        return patientActivityRepository.save(existing);
    }

    /** Assigns an open task to the current user. */
    public PatientActivity claim(Long id) {
        LOG.info("[CLAIM] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        existing.setAssignedTo(currentUsername());
        return patientActivityRepository.save(existing);
    }

    public PatientActivity markChecklistItem(Long id, PatientActivityChecklistDTO dto) {
        PatientActivity existing = findOpenActivity(id);
        List<PatientActivityChecklistItem> checklist = new ArrayList<>(existing.getChecklist());
        if (dto.getIndex() >= checklist.size()) {
            throw bad("Checklist item not found.", "patientActivity.checklist.notFound");
        }
        PatientActivityChecklistItem item = checklist.get(dto.getIndex());
        checklist.set(dto.getIndex(), new PatientActivityChecklistItem(item.getLabel(), dto.isDone()));
        existing.setChecklist(checklist);
        return patientActivityRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public PatientActivity findById(Long id) {
        return patientActivityRepository.findById(id)
                .orElseThrow(() -> bad("Activity not found.", "patientActivity.notFound"));
    }

    @Transactional(readOnly = true)
    public PatientActivitySummaryVM summarize(Long patientId) {
        validatePatient(patientId);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfTomorrow = startOfToday.plus(1, ChronoUnit.DAYS);
        return new PatientActivitySummaryVM(
                patientActivityRepository.countByPatientIdAndStatus(patientId, PatientActivityStatus.OPEN),
                patientActivityRepository.countByPatientIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThan(
                        patientId,
                        PatientActivityStatus.OPEN,
                        startOfToday,
                        startOfTomorrow
                ),
                patientActivityRepository.countByPatientIdAndStatusAndDueAtBefore(
                        patientId,
                        PatientActivityStatus.OPEN,
                        startOfToday
                ),
                patientActivityRepository.countByPatientIdAndStatus(patientId, PatientActivityStatus.DONE)
        );
    }

    @Transactional(readOnly = true)
    public Page<PatientActivity> findByPatient(
            Long patientId,
            String category,
            List<PatientActivityStatus> statuses,
            PatientActivitySource source,
            String search,
            Pageable pageable
    ) {
        validatePatient(patientId);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Specification<PatientActivity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("note")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("outcome"), "")), like),
                        cb.like(readableEnum(cb, root.get("activityType")), like),
                        cb.like(readableEnum(cb, root.get("category")), like),
                        cb.like(readableEnum(cb, root.get("status")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("createdBy"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return patientActivityRepository.findAll(specification, withDefaultSort(pageable));
    }

    private Expression<String> readableEnum(CriteriaBuilder cb, Path<?> path) {
        return cb.lower(cb.function(
                "replace",
                String.class,
                path.as(String.class),
                cb.literal("_"),
                cb.literal(" ")
        ));
    }

    /**
     * Writes one system activity for a source event, shaped by the template with the same code.
     * Nothing is written when that template is missing or inactive, or when a repeated call for the
     * same source type, source id, and activity type finds the existing row.
     */
    public void recordOccurred(
            Long patientId,
            Long encounterId,
            Long appointmentId,
            PatientActivityType activityType,
            String sourceType,
            Long sourceId,
            String note
    ) {
        if (patientId == null || activityType == null || sourceType == null || sourceType.isBlank() || sourceId == null) {
            return;
        }
        if (!patientRepository.existsById(patientId)) {
            LOG.warn("[RECORD] skipped type={} because patient {} was not found", activityType, patientId);
            return;
        }
        ActivityTemplate template = activityTemplateRepository.findByCode(activityType.code()).orElse(null);
        if (template == null || !Boolean.TRUE.equals(template.getIsActive())) {
            LOG.debug("[RECORD] skipped type={} because its template is missing or inactive", activityType);
            return;
        }
        if (patientActivityRepository.existsBySourceTypeAndSourceIdAndActivityType(sourceType, sourceId, template.getCode())) {
            return;
        }

        PatientActivityStatus status = defaultStatus(template);
        Instant now = Instant.now();
        Instant dueAt = null;
        if (status == PatientActivityStatus.OPEN) {
            dueAt = Objects.requireNonNullElse(activitySetupService.resolveDueAt(template, now), now);
        }

        PatientActivity activity = PatientActivity.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .appointmentId(appointmentId)
                .category(template.getCategoryCode())
                .activityType(template.getCode())
                .status(status)
                .source(PatientActivitySource.SYSTEM)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .priority(template.getDefaultPriority())
                .note(noteOrDefault(template, note))
                .dueAt(dueAt)
                .assignedRoles(new ArrayList<>(template.getAssigneeRoles()))
                .checklist(status == PatientActivityStatus.OPEN ? checklistOf(template) : new ArrayList<>())
                .build();

        if (status == PatientActivityStatus.DONE) {
            activity.setCompletedAt(now);
            activity.setCompletedBy(currentUsername());
        }

        PatientActivity saved = patientActivityRepository.save(activity);
        LOG.info(
                "[RECORD] PatientActivity id={} patientId={} type={} source={}/{}",
                saved.getId(),
                patientId,
                activityType,
                sourceType,
                sourceId
        );
    }

    public void recordDiagnosticTestActivity(Long orderTestId, PatientActivityType activityType, String note) {
        if (orderTestId == null || activityType == null) {
            return;
        }

        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(orderTestId).orElse(null);
        if (test == null || test.getOrderId() == null) {
            LOG.warn("[RECORD] skipped type={} because diagnostic order test {} was not found", activityType, orderTestId);
            return;
        }

        DiagnosticOrder order = diagnosticOrderRepository.findById(test.getOrderId()).orElse(null);
        if (order == null) {
            LOG.warn("[RECORD] skipped type={} because diagnostic order {} was not found", activityType, test.getOrderId());
            return;
        }

        recordOccurred(
                order.getPatientId(),
                order.getEncounterId(),
                null,
                activityType,
                SOURCE_DIAGNOSTIC_ORDER_TEST,
                orderTestId,
                note
        );
    }

    public void recordClaimStatus(ClaimRequest claim) {
        if (claim == null || claim.getId() == null || claim.getStatus() == null) {
            return;
        }

        if (claim.getStatus() == ClaimStatus.SUBMITTED) {
            recordOccurred(
                    claim.getPatientId(),
                    claim.getEncounterId(),
                    null,
                    PatientActivityType.CLAIM_SUBMISSION_FOLLOW_UP,
                    SOURCE_CLAIM,
                    claim.getId(),
                    "Claim submitted"
            );
            return;
        }

        if (claim.getStatus() == ClaimStatus.REJECTED || claim.getStatus() == ClaimStatus.FAILED) {
            recordOccurred(
                    claim.getPatientId(),
                    claim.getEncounterId(),
                    null,
                    PatientActivityType.CLAIM_REJECTION_RESOLUTION,
                    SOURCE_CLAIM,
                    claim.getId(),
                    "Claim rejected"
            );
        }
    }

    private PatientActivity findOpenStaffActivity(Long id) {
        PatientActivity existing = findOpenActivity(id);
        if (existing.getSource() != PatientActivitySource.STAFF) {
            throw bad("System activities cannot be edited.", "patientActivity.systemNotEditable");
        }
        return existing;
    }

    private PatientActivity findOpenActivity(Long id) {
        if (id == null || id <= 0) {
            throw bad("Activity id is required.", "patientActivity.id.invalid");
        }

        PatientActivity existing = patientActivityRepository.findById(id)
                .orElseThrow(() -> bad("Activity not found.", "patientActivity.notFound"));

        if (existing.getStatus() != PatientActivityStatus.OPEN) {
            throw bad("Only an open activity can be changed.", "patientActivity.notOpen");
        }
        return existing;
    }

    private void validatePatient(Long patientId) {
        if (patientId == null || patientId <= 0) {
            throw bad("Patient id is required.", "patient.id.invalid");
        }
        if (!patientRepository.existsById(patientId)) {
            throw bad("Patient not found.", "patient.notFound");
        }
    }

    private ActivityTemplate activeTemplate(String code) {
        ActivityTemplate template = activitySetupService.findTemplateByCode(code);
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw bad("This activity type is inactive.", "activityTemplate.inactive");
        }
        return template;
    }

    private void validateCreatorRole(ActivityTemplate template) {
        if (template.getCreatorRoles() == null || template.getCreatorRoles().isEmpty()) {
            return;
        }
        String role = userRepository.findByLogin(currentUsername())
                .map(user -> user.getJobRole() == null ? null : user.getJobRole().name())
                .orElse(null);
        if (role == null || !template.getCreatorRoles().contains(role)) {
            throw bad("Your job role can't create this activity type.", "patientActivity.creatorRole.denied");
        }
    }

    private String validateAssignee(String login) {
        String value = trimToNull(login);
        if (value == null) {
            return null;
        }
        if (userRepository.findByLogin(value).isEmpty()) {
            throw bad("Assigned user not found.", "patientActivity.assignee.notFound");
        }
        return value;
    }

    /** Returns the trimmed outcome after checking it against the template options. */
    private String validateOutcome(ActivityTemplate template, String outcome) {
        String value = trimToNull(outcome);
        if (template == null) {
            return value;
        }
        List<String> options = template.getOutcomeOptions();
        if (value == null && Boolean.TRUE.equals(template.getRequireOutcome())) {
            throw bad("Select an outcome.", "patientActivity.outcome.required");
        }
        if (value != null && options != null && !options.isEmpty() && !options.contains(value)) {
            throw bad("Outcome is not one of the configured options.", "patientActivity.outcome.invalid");
        }
        return value;
    }

    private void validateStatusOnCreate(PatientActivityStatus status) {
        if (status != PatientActivityStatus.OPEN && status != PatientActivityStatus.DONE) {
            throw bad("A new activity can only be open or done.", "patientActivity.status.invalid");
        }
    }

    private void validateDueDate(PatientActivityStatus status, Instant dueAt) {
        if (status == PatientActivityStatus.OPEN && dueAt == null) {
            throw bad("Due date is required for an open activity.", "patientActivity.dueDate.required");
        }
    }

    private void validateEncounter(Long patientId, Long encounterId) {
        if (encounterId == null) {
            return;
        }
        if (encounterId <= 0) {
            throw bad("Encounter id is invalid.", "encounter.id.invalid");
        }

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> bad("Encounter not found.", "encounter.notFound"));

        if (encounter.getPatient() == null || !patientId.equals(encounter.getPatient().getId())) {
            throw bad("Encounter does not belong to this patient.", "encounter.patient.mismatch");
        }
    }

    private void validateAppointment(Long patientId, Long appointmentId) {
        if (appointmentId == null) {
            return;
        }
        if (appointmentId <= 0) {
            throw bad("Appointment id is invalid.", "appointment.id.invalid");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> bad("Appointment not found.", "appointment.notFound"));

        if (appointment.getPatient() == null || !patientId.equals(appointment.getPatient().getId())) {
            throw bad("Appointment does not belong to this patient.", "appointment.patient.mismatch");
        }
    }

    private PatientActivityStatus defaultStatus(ActivityTemplate template) {
        return template.getKind() == ActivityTemplateKind.ACTIVITY
                ? PatientActivityStatus.DONE
                : PatientActivityStatus.OPEN;
    }

    private List<PatientActivityChecklistItem> checklistOf(ActivityTemplate template) {
        List<PatientActivityChecklistItem> items = new ArrayList<>();
        if (template.getChecklist() != null) {
            template.getChecklist().forEach(label -> items.add(new PatientActivityChecklistItem(label, false)));
        }
        return items;
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
    }

    /** A staff entry needs a note, unless its outcome already says what happened. */
    private String noteOrFallback(String note, String outcome) {
        String text = trimToNull(note);
        if (text == null) {
            text = outcome;
        }
        if (text == null) {
            throw bad("Note is required.", "patientActivity.note.required");
        }
        return text;
    }

    private String noteOrDefault(ActivityTemplate template, String note) {
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            text = template.getName();
        }
        if (text.length() > 2000) {
            return text.substring(0, 2000);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }

    private BadRequestAlertException bad(String message, String errorKey) {
        return new BadRequestAlertException(message, ENTITY, errorKey);
    }
}
