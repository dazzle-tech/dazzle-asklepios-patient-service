package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.CdtDentalActionDTO;
import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.service.helper.CdtDentalActionHelper;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records the configured dental visual effect of a completed planned treatment on the execution chart.
 *
 * <p>The only setup relationship that names a dental action is CDT code to dental action
 * (cdt_dental_action). Dental Procedures and Services carry no dental action; a planned treatment
 * always stores its CDT code, so that CDT is the single source. Nothing is inferred from names.
 * The effect is a normal tooth action (existing treatment, the item's own surface, same image as chart actions)
 * linked to the planned treatment, so chart and plan previews render it through the existing pipeline.
 * Runs inside the completion transaction: a failure rolls the chart effect back with the completion.
 */
@Service
@Transactional
public class PlannedTreatmentChartEffectService {

    private static final Logger LOG = LoggerFactory.getLogger(PlannedTreatmentChartEffectService.class);

    private final CdtDentalActionHelper cdtDentalActionHelper;
    private final DentalActionHelper dentalActionHelper;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;

    public PlannedTreatmentChartEffectService(
            CdtDentalActionHelper cdtDentalActionHelper,
            DentalActionHelper dentalActionHelper,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository
    ) {
        this.cdtDentalActionHelper = cdtDentalActionHelper;
        this.dentalActionHelper = dentalActionHelper;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
    }

    /**
     * Returns a short user-facing outcome. A setup lookup that cannot be answered aborts completion with a
     * retryable error; a valid "nothing configured" answer is reported and completion proceeds.
     */
    public String apply(DentalPlannedTreatment plan, List<DentalChartTooth> executionTeeth, String currentUser) {
        List<DentalActionSetupDTO> candidates = lookUpTreatmentActions(plan.getCdtCodeId());
        if (candidates.isEmpty()) {
            return "Completed. No chart effect: no active treatment dental action is configured for this CDT code.";
        }
        if (candidates.size() > 1) {
            return "Completed. No chart effect: more than one treatment dental action is configured for this CDT code.";
        }
        DentalActionSetupDTO action = candidates.get(0);
        if (action.imageName() == null || action.imageName().isBlank()) {
            return "Completed. No chart effect: the configured dental action has no image.";
        }
        // A mapped, valid effect from here on: anything that stops it from being recorded is stated, never skipped.
        if (plan.getTreatmentScope() != null && plan.getTreatmentScope() != TreatmentScope.SPECIFIC_TOOTH) {
            return "Completed. This multi-tooth, arch or full-mouth treatment was not drawn on the chart: "
                    + "the dental action setup has no whole-tooth representation for grouped treatments.";
        }
        // Ordinary chart actions always need a surface; Whole Tooth is a choice, never a default.
        ToothSurface surface = plan.getSurface();
        if (surface == null) {
            throw new BadRequestAlertException(
                    "Select a surface on this treatment before completing it, so it can be drawn on the chart.",
                    "dentalPlannedTreatment",
                    "surfaceRequiredForChartEffect"
            );
        }

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
            // Idempotency: this plan item's own effect on this execution tooth.
            if (toothActionRepository
                    .findBySourcePlannedTreatmentIdAndToothIdAndCancelledFalse(plan.getId(), tooth.getId()).isPresent()) {
                alreadyRecorded++;
                continue;
            }
            // The action this item was planned from, when it sits on this very tooth, already is its chart record.
            if (plan.getToothActionId() != null && toothActionRepository.findById(plan.getToothActionId())
                    .filter(a -> !a.isCancelled() && Objects.equals(a.getToothId(), tooth.getId())).isPresent()) {
                alreadyRecorded++;
                continue;
            }
            // Chart slots are unique per tooth + dental action + surface among active actions (uq_tooth_action_tooth_action_surface).
            // An unrelated action in this slot is a different clinical record: never overwritten, never claimed.
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
                    "dentalPlannedTreatment",
                    "chartEffectConflict"
            );
        }
        for (DentalChartTooth tooth : targets) {
            toothActionRepository.save(ToothAction.builder()
                    .toothId(tooth.getId())
                    .dentalActionId(action.id())
                    .surface(surface)
                    .existing(true)
                    .imageName(action.imageName())
                    .toothNumber(tooth.getToothNumber())
                    .visitNumber(plan.getVisitNumber())
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
        if (alreadyRecorded > 0) {
            return "Already shown on the chart.";
        }
        return missing > 0
                ? "Completed. Not drawn on the chart: the tooth is marked missing."
                : "Completed. Not drawn on the chart: no active tooth to record it on.";
    }

    private List<DentalActionSetupDTO> lookUpTreatmentActions(Long cdtCodeId) {
        try {
            List<DentalActionSetupDTO> found = new ArrayList<>();
            for (Long dentalActionId : cdtDentalActionHelper.getDentalActionsForCdt(cdtCodeId).stream()
                    .map(CdtDentalActionDTO::dentalActionId).filter(Objects::nonNull).distinct().toList()) {
                try {
                    DentalActionSetupDTO a = dentalActionHelper.getDentalAction(dentalActionId);
                    if (a != null && "TREATMENT".equalsIgnoreCase(a.type()) && !Boolean.FALSE.equals(a.isActive())) {
                        found.add(a);
                    }
                } catch (NotFoundAlertException missing) {
                    // A mapping to a deleted dental action is a valid "nothing usable" answer.
                }
            }
            return found;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            LOG.warn("Dental action lookup failed for CDT {}", cdtCodeId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The dental chart settings could not be read, so the treatment was not completed. Please try again."
            );
        }
    }
}
