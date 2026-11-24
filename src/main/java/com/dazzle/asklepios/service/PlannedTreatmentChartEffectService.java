package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentClinicalDetails;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Records the dental action selected at completion as a chart effect of a completed planned treatment.
 *
 * <p>The effect is a normal tooth action linked to the planned treatment and built with the same rules as a
 * Chart save: any active CONDITION or TREATMENT action, the item's own saved surface, the action's image name
 * copied as-is (it may be empty; the chart colours by action type and surface), and "existing" only for treatment
 * actions, as on the Chart form. Chart and plan previews render it through the existing pipeline.
 * Runs inside the completion transaction: any failure rolls the chart effect back with the completion.
 */
@Service
@Transactional
public class PlannedTreatmentChartEffectService {

    private static final String ENTITY = "dentalPlannedTreatment";
    private static final String CONDITION = "CONDITION";
    private static final String TREATMENT = "TREATMENT";

    /** Same encounter states the chart treats as closed; an amendment reopens the encounter to Ongoing. */
    private static final Set<TreatmentStatus> LOCKED_ENCOUNTER_STATUSES = Set.of(
            TreatmentStatus.COMPLETED,
            TreatmentStatus.DISCHARGED,
            TreatmentStatus.CLOSED,
            TreatmentStatus.CANCELLED
    );

    private final DentalActionHelper dentalActionHelper;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalChartRepository dentalChartRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;

    public PlannedTreatmentChartEffectService(
            DentalActionHelper dentalActionHelper,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository,
            DentalChartToothRepository dentalChartToothRepository,
            DentalChartRepository dentalChartRepository,
            ToothCdtRepository toothCdtRepository,
            ToothServiceRepository toothServiceRepository,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository
    ) {
        this.dentalActionHelper = dentalActionHelper;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.dentalChartRepository = dentalChartRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
    }

    /**
     * Returns a short user-facing outcome. Anything that stops the selected action from being recorded on the
     * saved tooth data throws, which aborts the completion.
     */
    public String apply(
            DentalPlannedTreatment plan,
            List<DentalChartTooth> executionTeeth,
            Long dentalActionId,
            String currentUser
    ) {
        DentalActionSetupDTO action = requireChartAction(dentalActionId);
        // Chart form: "Existing" is only available for treatment actions; conditions are saved without it.
        boolean existing = TREATMENT.equalsIgnoreCase(action.type());
        List<ToothSurface> planSurfaces = plan.surfaceValues();
        if (planSurfaces.isEmpty()) {
            throw new BadRequestAlertException(
                    "Select a surface on this treatment before completing it, so it can be drawn on the chart.",
                    ENTITY,
                    "surfaceRequiredForChartEffect"
            );
        }

        ToothSurface surface = planSurfaces.get(0);

        PlannedTreatmentClinicalDetails clinical = PlannedTreatmentClinicalDetails.fromJson(plan.getClinicalDetails());
        String dose = clinical.dose() == null
                ? null
                : (clinical.dose().stripTrailingZeros().toPlainString()
                + (clinical.unit() == null || clinical.unit().isBlank() ? "" : " " + clinical.unit()));

        List<DentalChartTooth> targets = new ArrayList<>();
        List<String> occupied = new ArrayList<>();
        int alreadyRecorded = 0;
        int missing = 0;
        for (DentalChartTooth tooth : executionTeeth) {
            if (!tooth.isActiveInventory()) {
                continue;
            }
            if (tooth.isMissing()) {
                missing++;
                continue;
            }
            if (toothActionRepository
                    .findBySourcePlannedTreatmentIdAndToothIdAndCancelledFalse(plan.getId(), tooth.getId()).isPresent()) {
                alreadyRecorded++;
                continue;
            }
            if (plan.getToothActionId() != null && toothActionRepository.findById(plan.getToothActionId())
                    .filter(a -> !a.isCancelled() && Objects.equals(a.getToothId(), tooth.getId())).isPresent()) {
                alreadyRecorded++;
                continue;
            }
            if (toothActionRepository
                    .findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(tooth.getId(), action.id(), surface).isPresent()) {
                occupied.add(tooth.getToothNumber());
                continue;
            }
            targets.add(tooth);
        }
        if (!occupied.isEmpty()) {
            throw new ConflictAlertException(
                    "The chart already has this action on the same surface of tooth " + String.join(", ", occupied)
                            + " as a separate record, so this treatment cannot be recorded there. "
                            + "Review that chart action before completing.",
                    ENTITY,
                    "chartEffectConflict"
            );
        }
        if (targets.isEmpty() && alreadyRecorded == 0) {
            throw new BadRequestAlertException(
                    missing > 0
                            ? "The tooth is marked missing, so the selected action cannot be drawn on the chart."
                            : "There is no active tooth to record the selected action on.",
                    ENTITY,
                    "chartEffectNoTarget"
            );
        }
        for (DentalChartTooth tooth : targets) {
            toothActionRepository.save(ToothAction.builder()
                    .toothId(tooth.getId())
                    .dentalActionId(action.id())
                    .surface(surface)
                    .existing(existing)
                    .imageName(action.imageName())
                    .toothNumber(tooth.getToothNumber())
                    .visitNumber(plan.getVisitNumber())
                    .note(plan.getNote())
                    .anesthesiaUsed(clinical.anesthesiaUsed())
                    .dose(dose)
                    .sourcePlannedTreatmentId(plan.getId())
                    .cancelled(false)
                    .build());
            toothActionLogRepository.save(ToothActionLog.builder()
                    .toothId(tooth.getId())
                    .dentalActionId(action.id())
                    .logType(ToothActionLogType.SAVE)
                    .logTime(Instant.now())
                    .logOwner(currentUser)
                    .build());
        }
        if (!targets.isEmpty()) {
            return "Chart updated on " + targets.size() + (targets.size() == 1 ? " tooth." : " teeth.");
        }
        return "Already shown on the chart.";
    }

    /**
     * Reverses exactly what {@link #apply} recorded for this treatment: the chart actions it created
     * (source planned treatment = this item) and their carried-forward copies on later charts. An earlier
     * chart action that the completion only reused carries no source link and is left untouched.
     *
     * <p>Everything is checked before anything changes. If an affected chart belongs to a closed encounter,
     * or a later record depends on an affected action, nothing is cancelled and a conflict is returned.
     * Cancelled rows are kept (soft cancel, with reason and a CANCEL log entry).
     */
    public String reverse(DentalPlannedTreatment plan, String reason, String currentUser) {
        List<ToothAction> effects = toothActionRepository.findBySourcePlannedTreatmentIdAndCancelledFalse(plan.getId());
        if (effects.isEmpty()) {
            return "No chart action was recorded by this completion.";
        }

        List<String> closed = new ArrayList<>();
        List<String> dependent = new ArrayList<>();
        for (ToothAction effect : effects) {
            DentalChartTooth tooth = dentalChartToothRepository.findById(effect.getToothId()).orElse(null);
            String label = tooth == null ? "#" + effect.getToothId() : tooth.getToothNumber();
            PatientEncounter encounter = tooth == null
                    ? null
                    : dentalChartRepository.findById(tooth.getChartId()).map(DentalChart::getEncounter).orElse(null);
            if (encounter == null || encounter.getStatus() == null
                    || LOCKED_ENCOUNTER_STATUSES.contains(encounter.getStatus())) {
                String where = encounter == null || encounter.getEncounterNumber() == null
                        ? "a closed encounter"
                        : "encounter " + encounter.getEncounterNumber();
                closed.add("tooth " + label + " (" + where + ")");
                continue;
            }
            if (!toothCdtRepository.findByToothActionIdAndCancelledFalse(effect.getId()).isEmpty()
                    || !toothServiceRepository.findByToothActionIdAndCancelledFalse(effect.getId()).isEmpty()
                    || !dentalPlannedTreatmentRepository.findByToothActionIdAndCancelledFalse(effect.getId()).isEmpty()) {
                dependent.add("tooth " + label);
            }
        }
        if (!closed.isEmpty()) {
            throw new ConflictAlertException(
                    "This treatment's chart record is on a closed encounter: " + String.join(", ", closed)
                            + ". Start an amendment of that encounter before returning the treatment to In Progress.",
                    ENTITY,
                    "returnEncounterClosed"
            );
        }
        if (!dependent.isEmpty()) {
            throw new ConflictAlertException(
                    "Later records depend on this treatment's chart action on " + String.join(", ", dependent)
                            + ". Review them before returning the treatment to In Progress.",
                    ENTITY,
                    "returnHasDependencies"
            );
        }

        Instant now = Instant.now();
        String cancellationReason = "Returned to In Progress: " + reason;
        for (ToothAction effect : effects) {
            effect.setCancelled(true);
            effect.setCancelledBy(currentUser);
            effect.setCancelledDate(now);
            effect.setCancellationReason(cancellationReason);
            toothActionRepository.save(effect);
            toothActionLogRepository.save(ToothActionLog.builder()
                    .toothId(effect.getToothId())
                    .dentalActionId(effect.getDentalActionId())
                    .logType(ToothActionLogType.CANCEL)
                    .logTime(now)
                    .logOwner(currentUser)
                    .build());
        }
        return "Removed from the chart (" + effects.size() + (effects.size() == 1 ? " record)." : " records).");
    }

    private DentalActionSetupDTO requireChartAction(Long dentalActionId) {
        if (dentalActionId == null) {
            throw new BadRequestAlertException("Select an action to complete this treatment.", ENTITY, "actionRequired");
        }
        DentalActionSetupDTO action;
        try {
            action = dentalActionHelper.getDentalAction(dentalActionId);
        } catch (NotFoundAlertException ex) {
            throw new BadRequestAlertException("The selected action does not exist.", ENTITY, "actionNotFound");
        }
        if (action == null || Boolean.FALSE.equals(action.isActive())) {
            throw new BadRequestAlertException("The selected action is not active.", ENTITY, "actionInactive");
        }
        // The chart draws only CONDITION (red) and TREATMENT (green) actions; anything else would not be shown.
        if (!CONDITION.equalsIgnoreCase(action.type()) && !TREATMENT.equalsIgnoreCase(action.type())) {
            throw new BadRequestAlertException(
                    "The selected action has an unsupported type and cannot be drawn on the chart.",
                    ENTITY,
                    "actionTypeUnsupported"
            );
        }
        return action;
    }
}
