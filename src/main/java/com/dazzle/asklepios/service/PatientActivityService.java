package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivitySource;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientActivityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCancelDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public PatientActivity create(PatientActivityCreateDTO dto) {
        LOG.info("[CREATE] PatientActivity patientId={} type={}", dto.getPatientId(), dto.getActivityType());

        validatePatient(dto.getPatientId());
        validateTypeMatchesCategory(dto.getActivityType(), dto.getCategory());
        validateStatusOnCreate(dto.getStatus());
        validateDueDate(dto.getStatus(), dto.getDueAt());
        validateEncounter(dto.getPatientId(), dto.getEncounterId());
        validateAppointment(dto.getPatientId(), dto.getAppointmentId());

        PatientActivityStatus status = dto.getStatus();
        String currentUser = currentUsername();

        PatientActivity activity = PatientActivity.builder()
                .patientId(dto.getPatientId())
                .encounterId(dto.getEncounterId())
                .appointmentId(dto.getAppointmentId())
                .category(dto.getCategory())
                .activityType(dto.getActivityType())
                .status(status)
                .source(PatientActivitySource.STAFF)
                .priority(resolvePriority(dto.getActivityType(), dto.getPriority()))
                .note(dto.getNote().trim())
                .dueAt(dto.getDueAt())
                .build();

        if (status == PatientActivityStatus.DONE) {
            activity.setCompletedAt(Instant.now());
            activity.setCompletedBy(currentUser);
        }

        PatientActivity saved = patientActivityRepository.save(activity);
        LOG.info("[CREATE] PatientActivity id={} patientId={}", saved.getId(), saved.getPatientId());
        return saved;
    }

    public PatientActivity update(Long id, PatientActivityUpdateDTO dto) {
        LOG.info("[UPDATE] PatientActivity id={}", id);

        PatientActivity existing = findOpenStaffActivity(id);
        validateTypeMatchesCategory(dto.getActivityType(), dto.getCategory());
        validateDueDate(PatientActivityStatus.OPEN, dto.getDueAt());
        validateEncounter(existing.getPatientId(), dto.getEncounterId());
        validateAppointment(existing.getPatientId(), dto.getAppointmentId());

        existing.setEncounterId(dto.getEncounterId());
        existing.setAppointmentId(dto.getAppointmentId());
        existing.setCategory(dto.getCategory());
        existing.setActivityType(dto.getActivityType());
        existing.setPriority(resolvePriority(dto.getActivityType(), dto.getPriority()));
        existing.setNote(dto.getNote().trim());
        existing.setDueAt(dto.getDueAt());

        return patientActivityRepository.save(existing);
    }

    public PatientActivity complete(Long id, PatientActivityCompleteDTO dto) {
        LOG.info("[COMPLETE] PatientActivity id={}", id);

        PatientActivity existing = findOpenActivity(id);
        existing.setStatus(PatientActivityStatus.DONE);
        existing.setCompletedAt(Instant.now());
        existing.setCompletedBy(currentUsername());
        existing.setOutcomeNote(dto.getOutcomeNote().trim());
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

    @Transactional(readOnly = true)
    public PatientActivity findById(Long id) {
        return patientActivityRepository.findById(id)
                .orElseThrow(() -> bad("Activity not found.", "patientActivity.notFound"));
    }

    @Transactional(readOnly = true)
    public PatientActivitySummaryVM summarize(Long patientId) {
        validatePatient(patientId);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new PatientActivitySummaryVM(
                patientActivityRepository.countByPatientIdAndStatus(patientId, PatientActivityStatus.OPEN),
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
            PatientActivityCategory category,
            PatientActivityStatus status,
            String search,
            Pageable pageable
    ) {
        validatePatient(patientId);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Specification<PatientActivity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("note")), like),
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
     * Writes one system activity for a source event. A repeated call for the same
     * source type, source id, and activity type leaves the existing row in place.
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
        if (patientActivityRepository.existsBySourceTypeAndSourceIdAndActivityType(sourceType, sourceId, activityType)) {
            return;
        }

        PatientActivityStatus status = activityType.defaultStatus();
        Instant now = Instant.now();

        PatientActivity activity = PatientActivity.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .appointmentId(appointmentId)
                .category(activityType.getCategory())
                .activityType(activityType)
                .status(status)
                .source(PatientActivitySource.SYSTEM)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .priority(activityType.defaultPriority())
                .note(noteOrDefault(activityType, note))
                .dueAt(status == PatientActivityStatus.OPEN ? now : null)
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

    private void validateTypeMatchesCategory(PatientActivityType activityType, PatientActivityCategory category) {
        if (activityType == null || category == null || !activityType.belongsTo(category)) {
            throw bad("Activity type does not belong to the selected category.", "patientActivity.type.mismatch");
        }
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

    private PatientActivityPriority resolvePriority(PatientActivityType activityType, PatientActivityPriority priority) {
        if (priority != null) {
            return priority;
        }
        return activityType.defaultPriority();
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

    private String noteOrDefault(PatientActivityType activityType, String note) {
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            text = activityType.name().replace('_', ' ').toLowerCase();
        }
        if (text.length() > 2000) {
            return text.substring(0, 2000);
        }
        return text;
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }

    private BadRequestAlertException bad(String message, String errorKey) {
        return new BadRequestAlertException(message, ENTITY, errorKey);
    }
}
