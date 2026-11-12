package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.DentalTreatmentPlan;
import com.dazzle.asklepios.domain.DentalTreatmentPlanStatusLog;
import com.dazzle.asklepios.domain.DentalTreatmentSession;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.DentalTreatmentPlanStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.DentalTreatmentPlanRepository;
import com.dazzle.asklepios.repository.DentalTreatmentPlanStatusLogRepository;
import com.dazzle.asklepios.repository.DentalTreatmentSessionRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanReasonDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanStatusLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentSessionDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DentalTreatmentPlanService {

    private static final String ENTITY = "dentalTreatmentPlan";
    private static final long PLAN_LOCK_SALT = 0x4454504CL;

    private static final EnumSet<DentalTreatmentPlanStatus> EDITABLE =
            EnumSet.of(DentalTreatmentPlanStatus.DRAFT, DentalTreatmentPlanStatus.PROPOSED);
    private static final EnumSet<DentalTreatmentPlanStatus> ACCEPTS_NEW_TREATMENTS =
            EnumSet.of(
                    DentalTreatmentPlanStatus.DRAFT,
                    DentalTreatmentPlanStatus.PROPOSED,
                    DentalTreatmentPlanStatus.ACCEPTED,
                    DentalTreatmentPlanStatus.IN_PROGRESS
            );
    private static final EnumSet<DentalTreatmentPlanStatus> EXECUTABLE =
            EnumSet.of(DentalTreatmentPlanStatus.ACCEPTED, DentalTreatmentPlanStatus.IN_PROGRESS);

    private final DentalTreatmentPlanRepository planRepository;
    private final DentalTreatmentSessionRepository sessionRepository;
    private final DentalTreatmentPlanStatusLogRepository statusLogRepository;
    private final DentalPlannedTreatmentRepository treatmentRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final EntityManager entityManager;
    private final DentalPlannedTreatmentService dentalPlannedTreatmentService;

    public DentalTreatmentPlanService(
            DentalTreatmentPlanRepository planRepository,
            DentalTreatmentSessionRepository sessionRepository,
            DentalTreatmentPlanStatusLogRepository statusLogRepository,
            DentalPlannedTreatmentRepository treatmentRepository,
            PatientEncounterRepository patientEncounterRepository,
            EntityManager entityManager,
            @Lazy DentalPlannedTreatmentService dentalPlannedTreatmentService
    ) {
        this.planRepository = planRepository;
        this.sessionRepository = sessionRepository;
        this.statusLogRepository = statusLogRepository;
        this.treatmentRepository = treatmentRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.entityManager = entityManager;
        this.dentalPlannedTreatmentService = dentalPlannedTreatmentService;
    }

    public List<DentalTreatmentPlanSummaryDTO> listForPatient(Long patientId) {
        return planRepository.findByPatientIdOrderByCreatedDateDesc(patientId).stream()
                .map(this::toSummary)
                .toList();
    }

    public DentalTreatmentPlanSummaryDTO get(Long id) {
        return toSummary(getPlan(id));
    }

    public DentalTreatmentPlanSummaryDTO create(DentalTreatmentPlanSaveDTO dto) {
        PatientEncounter encounter = requireEncounterForPatient(dto.originEncounterId(), dto.patientId());
        String title = dto.title() == null ? "" : dto.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestAlertException("Title is required", ENTITY, "titleRequired");
        }

        DentalTreatmentPlan plan = DentalTreatmentPlan.builder()
                .planNumber(nextPlanNumber())
                .patientId(dto.patientId())
                .originEncounterId(encounter.getId())
                .title(title)
                .notes(blankToNull(dto.notes()))
                .practitionerId(dto.practitionerId())
                .status(DentalTreatmentPlanStatus.DRAFT)
                .build();
        plan = planRepository.save(plan);
        logChange(plan, null, DentalTreatmentPlanStatus.DRAFT, null);
        return toSummary(plan);
    }

    public DentalTreatmentPlanSummaryDTO updateHeader(Long id, DentalTreatmentPlanSaveDTO dto) {
        DentalTreatmentPlan plan = getPlan(id);
        if (!EDITABLE.contains(plan.getStatus())) {
            throw new BadRequestAlertException(
                    "Only draft or proposed plans can be edited",
                    ENTITY,
                    "planNotEditable"
            );
        }
        PatientEncounter originEncounter =
                requireEncounterForPatient(dto.originEncounterId(), plan.getPatientId());
        String title = dto.title() == null ? "" : dto.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestAlertException("Title is required", ENTITY, "titleRequired");
        }
        plan.setOriginEncounterId(originEncounter.getId());
        plan.setTitle(title);
        plan.setNotes(blankToNull(dto.notes()));
        plan.setPractitionerId(dto.practitionerId());
        return toSummary(planRepository.save(plan));
    }

    public List<DentalTreatmentSessionDTO> listSessions(Long planId) {
        getPlan(planId);
        return sessionRepository.findByTreatmentPlanIdOrderBySessionNumberAsc(planId).stream()
                .map(s -> new DentalTreatmentSessionDTO(s.getId(), s.getTreatmentPlanId(), s.getSessionNumber()))
                .toList();
    }

    public List<DentalTreatmentPlanStatusLogDTO> statusLog(Long planId) {
        getPlan(planId);
        return statusLogRepository.findByTreatmentPlanIdOrderByChangedAtAsc(planId).stream()
                .map(DentalTreatmentPlanStatusLogDTO::from)
                .toList();
    }

    /**
     * Allocates one session for an add batch, or returns the selected session of this plan.
     * Numbers start at 1 and are independent per plan. Serialized per plan.
     */
    public DentalTreatmentSession resolveSession(Long planId, Long sessionId, boolean createNew) {
        DentalTreatmentPlan plan = getPlan(planId);
        assertAcceptsTreatments(plan);
        lockPlan(planId);
        if (sessionId != null) {
            DentalTreatmentSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Treatment session not found: " + sessionId,
                            "dentalTreatmentSession",
                            "notfound"
                    ));
            if (!Objects.equals(session.getTreatmentPlanId(), planId)) {
                throw new BadRequestAlertException(
                        "Session does not belong to the selected treatment plan",
                        ENTITY,
                        "sessionPlanMismatch"
                );
            }
            return session;
        }
        if (!createNew) {
            throw new BadRequestAlertException("A session is required", ENTITY, "sessionRequired");
        }
        Integer max = sessionRepository.findMaxSessionNumber(planId);
        int next = (max == null ? 0 : max) + 1;
        return sessionRepository.save(DentalTreatmentSession.builder()
                .treatmentPlanId(planId)
                .sessionNumber(next)
                .build());
    }

    public DentalTreatmentPlan requireExecutable(Long planId, Long patientId) {
        DentalTreatmentPlan plan = getPlan(planId);
        if (!Objects.equals(plan.getPatientId(), patientId)) {
            throw new BadRequestAlertException(
                    "Treatment plan does not belong to this patient",
                    ENTITY,
                    "planPatientMismatch"
            );
        }
        if (plan.getStatus() == DentalTreatmentPlanStatus.ON_HOLD) {
            throw new BadRequestAlertException(
                    "This plan is on hold. Resume it before starting or completing treatment.",
                    ENTITY,
                    "planOnHold"
            );
        }
        if (!EXECUTABLE.contains(plan.getStatus())) {
            throw new BadRequestAlertException(
                    "Accept the plan before starting or completing treatment",
                    ENTITY,
                    "planNotExecutable"
            );
        }
        return plan;
    }

    public void assertPlanForNewTreatment(Long planId, Long patientId) {
        DentalTreatmentPlan plan = getPlan(planId);
        if (!Objects.equals(plan.getPatientId(), patientId)) {
            throw new BadRequestAlertException(
                    "Treatment plan does not belong to this patient",
                    ENTITY,
                    "planPatientMismatch"
            );
        }
        assertAcceptsTreatments(plan);
    }

    public DentalTreatmentPlanSummaryDTO propose(Long id) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() != DentalTreatmentPlanStatus.DRAFT) {
            throw new BadRequestAlertException("Only a draft plan can be proposed", ENTITY, "invalidTransition");
        }
        if (activeTreatments(id).isEmpty()) {
            throw new BadRequestAlertException(
                    "Add at least one treatment before proposing the plan",
                    ENTITY,
                    "planEmpty"
            );
        }
        return transition(plan, DentalTreatmentPlanStatus.PROPOSED, null);
    }

    public DentalTreatmentPlanSummaryDTO accept(Long id) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() != DentalTreatmentPlanStatus.PROPOSED) {
            throw new BadRequestAlertException("Only a proposed plan can be accepted", ENTITY, "invalidTransition");
        }
        if (activeTreatments(id).isEmpty()) {
            throw new BadRequestAlertException(
                    "An empty plan cannot be accepted",
                    ENTITY,
                    "planEmpty"
            );
        }
        return transition(plan, DentalTreatmentPlanStatus.ACCEPTED, null);
    }

    public DentalTreatmentPlanSummaryDTO returnToDraft(Long id, DentalTreatmentPlanReasonDTO dto) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() != DentalTreatmentPlanStatus.PROPOSED) {
            throw new BadRequestAlertException(
                    "Only a proposed plan can be returned to draft",
                    ENTITY,
                    "invalidTransition"
            );
        }
        return transition(plan, DentalTreatmentPlanStatus.DRAFT, requiredReason(dto));
    }

    public DentalTreatmentPlanSummaryDTO hold(Long id, DentalTreatmentPlanReasonDTO dto) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() != DentalTreatmentPlanStatus.ACCEPTED
                && plan.getStatus() != DentalTreatmentPlanStatus.IN_PROGRESS) {
            throw new BadRequestAlertException(
                    "Only an accepted or in-progress plan can be put on hold",
                    ENTITY,
                    "invalidTransition"
            );
        }
        plan.setResumeStatus(plan.getStatus());
        plan.setHoldReason(requiredReason(dto));
        return transition(plan, DentalTreatmentPlanStatus.ON_HOLD, plan.getHoldReason());
    }

    public DentalTreatmentPlanSummaryDTO resume(Long id) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() != DentalTreatmentPlanStatus.ON_HOLD) {
            throw new BadRequestAlertException("Only an on-hold plan can be resumed", ENTITY, "invalidTransition");
        }
        DentalTreatmentPlanStatus restore = plan.getResumeStatus() != null
                ? plan.getResumeStatus()
                : DentalTreatmentPlanStatus.ACCEPTED;
        plan.setHoldReason(null);
        plan.setResumeStatus(null);
        return transition(plan, restore, null);
    }

    public DentalTreatmentPlanSummaryDTO cancel(Long id, DentalTreatmentPlanReasonDTO dto) {
        DentalTreatmentPlan plan = getPlan(id);
        if (plan.getStatus() == DentalTreatmentPlanStatus.COMPLETED
                || plan.getStatus() == DentalTreatmentPlanStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Completed or cancelled plans cannot be cancelled",
                    ENTITY,
                    "invalidTransition"
            );
        }
        String reason = requiredReason(dto);
        String user = currentUser();
        Instant now = Instant.now();
        for (DentalPlannedTreatment treatment : treatmentRepository.findByTreatmentPlanId(id)) {
            dentalPlannedTreatmentService.cascadeCancelOpen(treatment, reason, user);
        }
        plan.setCancelledBy(user);
        plan.setCancelledAt(now);
        plan.setCancellationReason(reason);
        return transition(plan, DentalTreatmentPlanStatus.CANCELLED, reason);
    }

    /**
     * Accepted plan moves to In Progress when the first treatment starts or is completed.
     */
    public void markInProgressIfAccepted(Long planId) {
        if (planId == null) {
            return;
        }
        DentalTreatmentPlan plan = getPlan(planId);
        if (plan.getStatus() == DentalTreatmentPlanStatus.ACCEPTED) {
            transition(plan, DentalTreatmentPlanStatus.IN_PROGRESS, null);
        }
    }

    /**
     * Completes the plan when every non-cancelled treatment is completed
     * and at least one treatment is genuinely completed.
     */
    public void evaluateCompletion(Long planId) {
        if (planId == null) {
            return;
        }
        DentalTreatmentPlan plan = getPlan(planId);
        if (plan.getStatus() != DentalTreatmentPlanStatus.IN_PROGRESS
                && plan.getStatus() != DentalTreatmentPlanStatus.ACCEPTED) {
            return;
        }
        List<DentalPlannedTreatment> rows = treatmentRepository.findByTreatmentPlanId(planId);
        List<DentalPlannedTreatment> active = rows.stream().filter(t -> !t.isCancelled()).toList();
        if (active.isEmpty()) {
            return;
        }
        boolean allCompleted = active.stream().allMatch(t ->
                t.getStatus() == PlannedTreatmentStatus.COMPLETED
                        || t.getPatientServiceAndProductId() != null);
        boolean anyCompleted = active.stream().anyMatch(t ->
                t.getStatus() == PlannedTreatmentStatus.COMPLETED
                        || t.getPatientServiceAndProductId() != null);
        if (allCompleted && anyCompleted) {
            plan.setCompletedBy(currentUser());
            plan.setCompletedAt(Instant.now());
            transition(plan, DentalTreatmentPlanStatus.COMPLETED, null);
        }
    }

    /**
     * Accepted or in-progress plans return to Proposed when commercial or
     * clinical scope of an unfinished treatment changes.
     */
    public void returnToProposedForRevision(Long planId, String reason) {
        DentalTreatmentPlan plan = getPlan(planId);
        if (plan.getStatus() != DentalTreatmentPlanStatus.ACCEPTED
                && plan.getStatus() != DentalTreatmentPlanStatus.IN_PROGRESS) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "A reason is required to change an accepted plan",
                    ENTITY,
                    "revisionReasonRequired"
            );
        }
        transition(plan, DentalTreatmentPlanStatus.PROPOSED, reason.trim());
    }

    public PatientEncounter requireEncounterForPatient(Long encounterId, Long patientId) {
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        ENTITY,
                        "encounterNotFound"
                ));
        if (encounter.getPatient() == null || !Objects.equals(encounter.getPatient().getId(), patientId)) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to this patient",
                    ENTITY,
                    "encounterPatientMismatch"
            );
        }
        return encounter;
    }

    private void assertAcceptsTreatments(DentalTreatmentPlan plan) {
        if (plan.getStatus() == DentalTreatmentPlanStatus.ON_HOLD) {
            throw new BadRequestAlertException(
                    "This plan is on hold. Resume it before adding treatment.",
                    ENTITY,
                    "planOnHold"
            );
        }
        if (!ACCEPTS_NEW_TREATMENTS.contains(plan.getStatus())) {
            throw new BadRequestAlertException(
                    "This plan cannot accept new treatments",
                    ENTITY,
                    "planClosed"
            );
        }
    }

    private List<DentalPlannedTreatment> activeTreatments(Long planId) {
        return treatmentRepository.findByTreatmentPlanId(planId).stream()
                .filter(t -> !t.isCancelled())
                .toList();
    }

    private DentalTreatmentPlanSummaryDTO transition(
            DentalTreatmentPlan plan,
            DentalTreatmentPlanStatus next,
            String reason
    ) {
        DentalTreatmentPlanStatus previous = plan.getStatus();
        plan.setStatus(next);
        DentalTreatmentPlan saved = planRepository.save(plan);
        if (previous != next) {
            logChange(saved, previous, next, reason);
        }
        return toSummary(saved);
    }

    private void logChange(
            DentalTreatmentPlan plan,
            DentalTreatmentPlanStatus previous,
            DentalTreatmentPlanStatus next,
            String reason
    ) {
        statusLogRepository.save(DentalTreatmentPlanStatusLog.builder()
                .treatmentPlanId(plan.getId())
                .previousStatus(previous)
                .newStatus(next)
                .reason(blankToNull(reason))
                .changedBy(currentUser())
                .changedAt(Instant.now())
                .build());
    }

    private DentalTreatmentPlanSummaryDTO toSummary(DentalTreatmentPlan plan) {
        List<DentalPlannedTreatment> rows = plan.getId() == null
                ? List.of()
                : treatmentRepository.findByTreatmentPlanId(plan.getId());
        int active = 0;
        int completed = 0;
        for (DentalPlannedTreatment row : rows) {
            if (row.isCancelled()) {
                continue;
            }
            active++;
            if (row.getStatus() == PlannedTreatmentStatus.COMPLETED
                    || row.getPatientServiceAndProductId() != null) {
                completed++;
            }
        }
        String encounterNumber = patientEncounterRepository.findById(plan.getOriginEncounterId())
                .map(PatientEncounter::getEncounterNumber)
                .orElse(null);
        return new DentalTreatmentPlanSummaryDTO(
                plan.getId(),
                plan.getPlanNumber(),
                plan.getPatientId(),
                plan.getOriginEncounterId(),
                encounterNumber,
                plan.getTitle(),
                plan.getNotes(),
                plan.getPractitionerId(),
                plan.getStatus(),
                plan.getResumeStatus(),
                plan.getHoldReason(),
                plan.getCancellationReason(),
                plan.getCreatedDate(),
                plan.getCompletedAt(),
                plan.getCompletedBy(),
                completed,
                active
        );
    }

    private String nextPlanNumber() {
        Long seq = planRepository.nextPlanSequence();
        return "DTP-" + String.format("%05d", seq);
    }

    private void lockPlan(Long planId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", PLAN_LOCK_SALT ^ planId)
                .getSingleResult();
    }

    private DentalTreatmentPlan getPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Treatment plan not found: " + id,
                        ENTITY,
                        "notfound"
                ));
    }

    private String requiredReason(DentalTreatmentPlanReasonDTO dto) {
        if (dto == null || dto.reason() == null || dto.reason().isBlank()) {
            throw new BadRequestAlertException("A reason is required", ENTITY, "reasonRequired");
        }
        return dto.reason().trim();
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
