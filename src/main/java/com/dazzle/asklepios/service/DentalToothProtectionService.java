package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentSpecification;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.repository.projection.DentalChartProjections;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentDTO;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which chart teeth carry recorded clinical work and therefore cannot be removed or replaced
 * through inventory editing.
 *
 * <p>A tooth is protected when it has at least one of:
 * <ul>
 *   <li>a non-cancelled tooth action (finding, treatment or carried-forward finding);</li>
 *   <li>a non-cancelled tooth service or tooth CDT record;</li>
 *   <li>a non-cancelled planned treatment that is IN_PROGRESS or COMPLETED and whose saved scope
 *       covers it: the single tooth, or every saved tooth of a multi-tooth, arch or full-mouth item.
 *       On the chart where the treatment is executed, the covered teeth match by tooth number.</li>
 * </ul>
 * Cancelled records, PENDING proposals, action logs and inventory audit rows are not work.
 * A CDT record normally hangs off a tooth action but is checked on its own, so it protects even if its action row is gone.
 */
@Service
@Transactional(readOnly = true)
public class DentalToothProtectionService {

    private static final List<PlannedTreatmentStatus> WORK_STATUSES =
            List.of(PlannedTreatmentStatus.IN_PROGRESS, PlannedTreatmentStatus.COMPLETED);

    private final ToothActionRepository toothActionRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;

    public DentalToothProtectionService(
            ToothActionRepository toothActionRepository,
            ToothServiceRepository toothServiceRepository,
            ToothCdtRepository toothCdtRepository,
            DentalChartToothRepository dentalChartToothRepository,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository
    ) {
        this.toothActionRepository = toothActionRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
    }

    /**
     * The teeth a planned treatment actually covers, as saved with it: the single tooth, or the saved
     * covered-tooth ids of a multi-tooth, arch or full-mouth item. Completion resolves grouped coverage
     * from the same saved ids, so the two always agree. A grouped legacy row with no saved ids is not
     * re-derived from the current chart; it yields no teeth here.
     */
    private static List<Long> savedCoverageToothIds(DentalPlannedTreatment plan) {
        if (plan.getTreatmentScope() == null || plan.getTreatmentScope() == TreatmentScope.SPECIFIC_TOOTH) {
            return plan.getToothId() == null ? List.of() : List.of(plan.getToothId());
        }
        return PlannedTreatmentDTO.parseCoveredToothIds(plan.getCoveredToothIds());
    }

    /**
     * Started or completed grouped treatments with no saved coverage cannot say which teeth they involve.
     * Removing teeth from a chart they touch is refused until their coverage is reviewed.
     */
    public void requireResolvedCoverage(DentalChart chart) {
        Long encounterId = chart.getEncounter().getId();
        for (DentalPlannedTreatment plan : dentalPlannedTreatmentRepository.findAll(
                DentalPlannedTreatmentSpecification.touchingEncounter(encounterId, WORK_STATUSES))) {
            boolean grouped = plan.getTreatmentScope() != null && plan.getTreatmentScope() != TreatmentScope.SPECIFIC_TOOTH;
            if (grouped && PlannedTreatmentDTO.parseCoveredToothIds(plan.getCoveredToothIds()).isEmpty()) {
                throw new ConflictAlertException(
                        "A started or completed multi-tooth, arch or full-mouth treatment on this chart has no saved "
                                + "tooth coverage. Review its coverage before removing or replacing teeth.",
                        "dentalChartInventory",
                        "inventory.coverageNeedsReview"
                );
            }
        }
    }

    /** Tooth id to a short reason, only for teeth that carry recorded work. */
    public Map<Long, String> reasonsByToothId(DentalChart chart, Collection<DentalChartTooth> teeth) {
        if (teeth.isEmpty()) {
            return Map.of();
        }
        List<Long> toothIds = teeth.stream().map(DentalChartTooth::getId).toList();
        Set<Long> withActions = toothIds(toothActionRepository.findDistinctByToothIdInAndCancelledFalse(toothIds));
        Set<Long> withCdts = toothIds(toothCdtRepository.findDistinctByToothIdInAndCancelledFalse(toothIds));
        Set<Long> withServices = toothIds(toothServiceRepository.findDistinctByToothIdInAndCancelledFalse(toothIds));
        Long encounterId = chart.getEncounter().getId();
        Set<Long> withPlanned = new HashSet<>();
        Set<Long> executedOnThisChart = new HashSet<>();
        for (DentalPlannedTreatment plan : dentalPlannedTreatmentRepository.findAll(
                DentalPlannedTreatmentSpecification.touchingEncounter(encounterId, WORK_STATUSES))) {
            List<Long> covered = savedCoverageToothIds(plan);
            if (Objects.equals(plan.getEncounterId(), encounterId)) {
                withPlanned.addAll(covered);
            }
            if (Objects.equals(plan.getExecutionEncounterId(), encounterId)) {
                executedOnThisChart.addAll(covered);
            }
        }
        // Execution on this chart covers the same tooth numbers as the saved (planning-chart) teeth.
        Set<String> executedNumbers = new HashSet<>();
        if (!executedOnThisChart.isEmpty()) {
            dentalChartToothRepository.findAllById(executedOnThisChart)
                    .forEach(t -> executedNumbers.add(t.getToothNumber()));
        }

        Map<Long, String> reasons = new HashMap<>();
        for (DentalChartTooth tooth : teeth) {
            List<String> parts = new ArrayList<>();
            if (withActions.contains(tooth.getId())) {
                parts.add("findings or treatments");
            }
            if (withServices.contains(tooth.getId()) || withCdts.contains(tooth.getId())) {
                parts.add("services or CDT codes");
            }
            if (withPlanned.contains(tooth.getId()) || executedNumbers.contains(tooth.getToothNumber())) {
                parts.add("treatment in progress or completed");
            }
            if (!parts.isEmpty()) {
                reasons.put(tooth.getId(), "Has recorded work (" + String.join(", ", parts) + ")");
            }
        }
        return reasons;
    }

    private static Set<Long> toothIds(List<DentalChartProjections.ToothIdView> views) {
        return views.stream().map(DentalChartProjections.ToothIdView::getToothId).collect(Collectors.toSet());
    }
}
