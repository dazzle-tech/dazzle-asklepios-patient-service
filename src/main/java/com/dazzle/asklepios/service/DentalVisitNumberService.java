package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.projection.VisitNumberView;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Patient-scoped Visit Number allocation for dental planned treatments and Existing chart treatments.
 * Manual values are preserved; omitted values get max(existing including cancelled) + 1.
 * Concurrent automatic allocations for the same patient are serialized with a Postgres
 * advisory transaction lock.
 */
@Service
@Transactional
public class DentalVisitNumberService {

    private static final String ENTITY = "dentalPlannedTreatment";
    /**
     * Salt so this lock does not collide with other patient-scoped advisory locks
     * that use the raw patient id (e.g. wallet ledger).
     */
    private static final long LOCK_SALT = 0x44564E54L; // DVNT

    private final EntityManager entityManager;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final ToothActionRepository toothActionRepository;
    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;

    public DentalVisitNumberService(
            EntityManager entityManager,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            ToothActionRepository toothActionRepository,
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository
    ) {
        this.entityManager = entityManager;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.toothActionRepository = toothActionRepository;
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
    }

    /**
     * Distinct positive Visit Numbers used by the patient on planned treatments and tooth actions, ascending.
     */
    @Transactional(readOnly = true)
    public List<Integer> listPatientVisitNumbers(Long patientId) {
        Set<Integer> numbers = new TreeSet<>();
        dentalPlannedTreatmentRepository.findDistinctByPatientIdAndVisitNumberNotNull(patientId).stream()
                .map(VisitNumberView::getVisitNumber)
                .filter(n -> n != null && n > 0)
                .forEach(numbers::add);
        List<Long> toothIds = patientChartToothIds(patientId);
        if (!toothIds.isEmpty()) {
            toothActionRepository.findDistinctByToothIdInAndVisitNumberNotNull(toothIds).stream()
                    .map(VisitNumberView::getVisitNumber)
                    .filter(n -> n != null && n > 0)
                    .forEach(numbers::add);
        }
        return new ArrayList<>(numbers);
    }

    /**
     * Create path: keep a supplied positive number; otherwise allocate the next number.
     */
    public Integer resolveForCreate(Long patientId, Integer suppliedVisitNumber) {
        if (suppliedVisitNumber != null) {
            validatePositive(suppliedVisitNumber);
            return suppliedVisitNumber;
        }
        return allocateNext(patientId);
    }

    /**
     * Update path: keep a supplied positive number; if omitted, preserve the saved value.
     * Legacy rows with a null saved value receive an automatic number once.
     */
    public Integer resolveForUpdate(
            Long patientId,
            Integer suppliedVisitNumber,
            Integer currentVisitNumber
    ) {
        if (suppliedVisitNumber != null) {
            validatePositive(suppliedVisitNumber);
            return suppliedVisitNumber;
        }
        if (currentVisitNumber != null) {
            return currentVisitNumber;
        }
        return allocateNext(patientId);
    }

    /**
     * Allocate the next Visit Number for the patient (used by chart CDT fan-out once per action).
     */
    public Integer allocateNext(Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient is required to allocate a visit number",
                    ENTITY,
                    "patientRequiredForVisitNumber"
            );
        }
        lockPatientVisitSequence(patientId);
        return currentMaxVisitNumber(patientId) + 1;
    }

    /**
     * Persist Visit Numbers on Existing chart actions that lack one.
     * Linked non-cancelled plans reuse their Visit Number (no second allocation).
     * Remaining actions are numbered once in createdDate/id order after the patient max.
     */
    public void ensureExistingActionVisitNumbers(Long patientId, List<ToothAction> existingTreatmentActions) {
        if (patientId == null || existingTreatmentActions == null || existingTreatmentActions.isEmpty()) {
            return;
        }

        boolean anyMissing = existingTreatmentActions.stream()
                .anyMatch(action -> action != null && action.getVisitNumber() == null);
        if (!anyMissing) {
            return;
        }

        lockPatientVisitSequence(patientId);

        List<ToothAction> toPersist = new ArrayList<>();
        List<ToothAction> needingAllocation = new ArrayList<>();

        for (ToothAction action : existingTreatmentActions) {
            if (action == null || action.getVisitNumber() != null) {
                continue;
            }

            Integer linkedVisitNumber = linkedPlanVisitNumber(action.getId());
            if (linkedVisitNumber != null) {
                action.setVisitNumber(linkedVisitNumber);
                toPersist.add(action);
                continue;
            }

            needingAllocation.add(action);
        }

        needingAllocation.sort(
                Comparator
                        .comparing(ToothAction::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ToothAction::getId, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        int next = currentMaxVisitNumber(patientId) + 1;
        for (ToothAction action : needingAllocation) {
            action.setVisitNumber(next++);
            toPersist.add(action);
        }

        if (!toPersist.isEmpty()) {
            toothActionRepository.saveAll(toPersist);
        }
    }

    /**
     * Assign a Visit Number to a single Existing chart treatment action if missing.
     */
    public Integer ensureExistingActionVisitNumber(Long patientId, ToothAction action) {
        if (action == null) {
            return null;
        }
        if (action.getVisitNumber() != null) {
            return action.getVisitNumber();
        }
        ensureExistingActionVisitNumbers(patientId, List.of(action));
        return action.getVisitNumber();
    }

    public void validatePositive(Integer visitNumber) {
        if (visitNumber == null) {
            return;
        }
        if (visitNumber <= 0) {
            throw new BadRequestAlertException(
                    "Visit number must be a positive integer",
                    ENTITY,
                    "invalidVisitNumber"
            );
        }
    }

    private Integer linkedPlanVisitNumber(Long toothActionId) {
        if (toothActionId == null) {
            return null;
        }
        return dentalPlannedTreatmentRepository.findByToothActionIdAndCancelledFalse(toothActionId).stream()
                .map(DentalPlannedTreatment::getVisitNumber)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private int currentMaxVisitNumber(Long patientId) {
        int planMax = dentalPlannedTreatmentRepository
                .findFirstByPatientIdAndVisitNumberNotNullOrderByVisitNumberDesc(patientId)
                .map(DentalPlannedTreatment::getVisitNumber)
                .orElse(0);
        List<Long> toothIds = patientChartToothIds(patientId);
        int actionMax = toothIds.isEmpty()
                ? 0
                : toothActionRepository
                        .findFirstByToothIdInAndVisitNumberNotNullOrderByVisitNumberDesc(toothIds)
                        .map(ToothAction::getVisitNumber)
                        .orElse(0);
        return Math.max(planMax, actionMax);
    }

    /** IDs of every chart tooth across all of the patient's dental charts. */
    private List<Long> patientChartToothIds(Long patientId) {
        List<Long> chartIds = dentalChartRepository.findByPatient_Id(patientId).stream()
                .map(DentalChart::getId)
                .toList();
        if (chartIds.isEmpty()) {
            return List.of();
        }
        return dentalChartToothRepository.findByChartIdIn(chartIds).stream()
                .map(DentalChartTooth::getId)
                .toList();
    }

    private void lockPatientVisitSequence(Long patientId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_SALT ^ patientId)
                .getSingleResult();
    }
}
