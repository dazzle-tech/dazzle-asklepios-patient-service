package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannedTreatmentChartEffectServiceTest {

    private static final Long PLAN_ID = 100L;
    private static final Long ACTION_ID = 7L;
    private static final Long TOOTH_ID = 1L;

    private DentalActionHelper dentalActionHelper;
    private ToothActionRepository toothActionRepository;
    private ToothActionLogRepository toothActionLogRepository;
    private DentalChartToothRepository dentalChartToothRepository;
    private DentalChartRepository dentalChartRepository;
    private PlannedTreatmentChartEffectService service;

    @BeforeEach
    void setUp() {
        dentalActionHelper = mock(DentalActionHelper.class);
        toothActionRepository = mock(ToothActionRepository.class);
        toothActionLogRepository = mock(ToothActionLogRepository.class);
        dentalChartToothRepository = mock(DentalChartToothRepository.class);
        dentalChartRepository = mock(DentalChartRepository.class);
        service = new PlannedTreatmentChartEffectService(
                dentalActionHelper,
                toothActionRepository,
                toothActionLogRepository,
                dentalChartToothRepository,
                dentalChartRepository,
                mock(ToothCdtRepository.class),
                mock(ToothServiceRepository.class),
                mock(DentalPlannedTreatmentRepository.class)
        );
        when(dentalActionHelper.getDentalAction(ACTION_ID))
                .thenReturn(new DentalActionSetupDTO(ACTION_ID, "Filling", "TREATMENT", "filling", true));
    }

    @Test
    void oneSelectedSurfaceCreatesOneChartAction() {
        String result = service.apply(plan(ToothSurface.OCCLUSAL), List.of(tooth()), ACTION_ID, "dr");

        assertThat(savedSurfaces()).containsExactly(ToothSurface.OCCLUSAL);
        assertThat(result).isEqualTo("Chart updated on 1 tooth.");
        verify(toothActionLogRepository, times(1)).save(any());
    }

    @Test
    void everySelectedSurfaceCreatesItsOwnChartAction() {
        String result = service.apply(
                plan(ToothSurface.MESIAL, ToothSurface.OCCLUSAL, ToothSurface.DISTAL),
                List.of(tooth()),
                ACTION_ID,
                "dr"
        );

        assertThat(savedSurfaces())
                .containsExactlyInAnyOrder(ToothSurface.MESIAL, ToothSurface.OCCLUSAL, ToothSurface.DISTAL);
        assertThat(result).isEqualTo("Chart updated on 1 tooth.");
        verify(toothActionLogRepository, times(3)).save(any());
    }

    @Test
    void savedActionsPointBackToThePlannedTreatment() {
        service.apply(plan(ToothSurface.MESIAL, ToothSurface.DISTAL), List.of(tooth()), ACTION_ID, "dr");

        ArgumentCaptor<ToothAction> saved = ArgumentCaptor.forClass(ToothAction.class);
        verify(toothActionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(action -> {
            assertThat(action.getSourcePlannedTreatmentId()).isEqualTo(PLAN_ID);
            assertThat(action.getToothId()).isEqualTo(TOOTH_ID);
            assertThat(action.getDentalActionId()).isEqualTo(ACTION_ID);
            assertThat(action.isCancelled()).isFalse();
        });
    }

    @Test
    void onlyMissingSurfacesAreCreatedWhenSomeAreAlreadyRecorded() {
        when(toothActionRepository.findBySourcePlannedTreatmentIdAndCancelledFalse(PLAN_ID))
                .thenReturn(List.of(recorded(1L, ToothSurface.MESIAL)));

        service.apply(plan(ToothSurface.MESIAL, ToothSurface.DISTAL), List.of(tooth()), ACTION_ID, "dr");

        assertThat(savedSurfaces()).containsExactly(ToothSurface.DISTAL);
    }

    @Test
    void completingAgainDoesNotDuplicateChartActions() {
        when(toothActionRepository.findBySourcePlannedTreatmentIdAndCancelledFalse(PLAN_ID))
                .thenReturn(List.of(recorded(1L, ToothSurface.MESIAL), recorded(2L, ToothSurface.DISTAL)));

        String result = service.apply(
                plan(ToothSurface.MESIAL, ToothSurface.DISTAL), List.of(tooth()), ACTION_ID, "dr");

        assertThat(result).isEqualTo("Already shown on the chart.");
        verify(toothActionRepository, never()).save(any());
        verify(toothActionLogRepository, never()).save(any());
    }

    @Test
    void surfaceOfTheSourceChartActionIsNotRecordedAgain() {
        DentalPlannedTreatment plan = plan(ToothSurface.OCCLUSAL, ToothSurface.DISTAL);
        plan.setToothActionId(50L);
        ToothAction source = recorded(50L, ToothSurface.OCCLUSAL);
        source.setSourcePlannedTreatmentId(null);
        when(toothActionRepository.findById(50L)).thenReturn(Optional.of(source));

        service.apply(plan, List.of(tooth()), ACTION_ID, "dr");

        assertThat(savedSurfaces()).containsExactly(ToothSurface.DISTAL);
    }

    @Test
    void surfaceChangedAfterPlanningFromAChartActionIsRecorded() {
        DentalPlannedTreatment plan = plan(ToothSurface.DISTAL);
        plan.setToothActionId(50L);
        ToothAction source = recorded(50L, ToothSurface.OCCLUSAL);
        when(toothActionRepository.findById(50L)).thenReturn(Optional.of(source));

        service.apply(plan, List.of(tooth()), ACTION_ID, "dr");

        assertThat(savedSurfaces()).containsExactly(ToothSurface.DISTAL);
    }

    @Test
    void conflictOnOneSurfaceStopsBeforeAnythingIsSaved() {
        when(toothActionRepository.findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(
                TOOTH_ID, ACTION_ID, ToothSurface.DISTAL)).thenReturn(Optional.of(recorded(9L, ToothSurface.DISTAL)));

        assertThrows(ConflictAlertException.class, () -> service.apply(
                plan(ToothSurface.MESIAL, ToothSurface.DISTAL), List.of(tooth()), ACTION_ID, "dr"));

        verify(toothActionRepository, never()).save(any());
        verify(toothActionLogRepository, never()).save(any());
    }

    @Test
    void treatmentWithoutSurfacesCannotBeDrawn() {
        assertThrows(BadRequestAlertException.class,
                () -> service.apply(plan(), List.of(tooth()), ACTION_ID, "dr"));

        verify(toothActionRepository, never()).save(any());
    }

    @Test
    void returnToInProgressCancelsEveryChartActionOfTheTreatment() {
        ToothAction mesial = recorded(1L, ToothSurface.MESIAL);
        ToothAction distal = recorded(2L, ToothSurface.DISTAL);
        when(toothActionRepository.findBySourcePlannedTreatmentIdAndCancelledFalse(PLAN_ID))
                .thenReturn(List.of(mesial, distal));
        when(dentalChartToothRepository.findById(TOOTH_ID))
                .thenReturn(Optional.of(DentalChartTooth.builder().id(TOOTH_ID).toothNumber("18").chartId(5L).build()));
        PatientEncounter encounter = PatientEncounter.builder().status(TreatmentStatus.ONGOING).build();
        when(dentalChartRepository.findById(5L))
                .thenReturn(Optional.of(DentalChart.builder().encounter(encounter).build()));

        String result = service.reverse(plan(ToothSurface.MESIAL, ToothSurface.DISTAL), "mistake", "dr");

        assertThat(mesial.isCancelled()).isTrue();
        assertThat(distal.isCancelled()).isTrue();
        assertThat(result).isEqualTo("Removed from the chart (2 records).");
        verify(toothActionRepository, times(2)).save(any(ToothAction.class));
        ArgumentCaptor<ToothActionLog> logs = ArgumentCaptor.forClass(ToothActionLog.class);
        verify(toothActionLogRepository, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).allSatisfy(log -> assertThat(log.getLogType()).isEqualTo(ToothActionLogType.CANCEL));
    }

    private List<ToothSurface> savedSurfaces() {
        ArgumentCaptor<ToothAction> saved = ArgumentCaptor.forClass(ToothAction.class);
        verify(toothActionRepository, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().stream().map(ToothAction::getSurface).toList();
    }

    private DentalPlannedTreatment plan(ToothSurface... surfaces) {
        return DentalPlannedTreatment.builder()
                .id(PLAN_ID)
                .surfaces(new ArrayList<>(List.of(surfaces)))
                .build();
    }

    private DentalChartTooth tooth() {
        return DentalChartTooth.builder().id(TOOTH_ID).toothNumber("18").chartId(5L).build();
    }

    private ToothAction recorded(Long id, ToothSurface surface) {
        return ToothAction.builder()
                .id(id)
                .toothId(TOOTH_ID)
                .dentalActionId(ACTION_ID)
                .surface(surface)
                .sourcePlannedTreatmentId(PLAN_ID)
                .build();
    }
}
