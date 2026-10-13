package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartProgressNote;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.PrimaryToothNumber;
import com.dazzle.asklepios.domain.enumeration.ToothNumber;
import com.dazzle.asklepios.repository.DentalChartProgressNoteRepository;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.service.dto.dentalChart.ChartUpdateDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartToothDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothCdtDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothServiceDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class DentalChartService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalChartService.class);

    // Patients younger than this get a primary (deciduous/"baby") tooth set
    // instead of the permanent 32-tooth set. Mixed dentition (roughly ages
    // 6-12, where both sets are partially present) is not modeled yet --
    // this is a simple binary split pending a real clinical requirement.
    private static final int CHILD_AGE_THRESHOLD = 13;

    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final DentalChartProgressNoteRepository dentalChartProgressNoteRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public DentalChartService(
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository,
            ToothCdtRepository toothCdtRepository,
            ToothServiceRepository toothServiceRepository,
            DentalChartProgressNoteRepository dentalChartProgressNoteRepository,
            PatientEncounterRepository patientEncounterRepository
    ) {
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.dentalChartProgressNoteRepository = dentalChartProgressNoteRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public DentalChartResponseDTO getOrCreateChartForEncounter(Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);

        return dentalChartRepository.findByEncounter_Id(encounterId)
                .map(this::assembleChart)
                .orElseGet(() -> createChartForEncounter(encounter));
    }

    @Transactional(readOnly = true)
    public DentalChartResponseDTO getChartById(Long chartId) {
        DentalChart chart = getChart(chartId);
        return assembleChart(chart);
    }

    public DentalChartResponseDTO updateChart(Long chartId, ChartUpdateDTO dto) {
        DentalChart chart = getChart(chartId);

        chart.setChartDate(dto.chartDate());
        chart.setChartType(dto.chartType());
        chart.setStatus(dto.status());
        chart.setTreatmentPlan(dto.treatmentPlan());

        DentalChart saved = dentalChartRepository.saveAndFlush(chart);
        return assembleChart(saved);
    }

    private DentalChartResponseDTO createChartForEncounter(PatientEncounter encounter) {
        Patient patient = encounter.getPatient();
        DentalChartType chartType = resolveChartType(patient);

        DentalChart newChart = DentalChart.builder()
                .patient(patient)
                .encounter(encounter)
                .chartType(chartType)
                .status(DentalChartStatus.NEW)
                .chartDate(Instant.now())
                .build();

        newChart = dentalChartRepository.saveAndFlush(newChart);

        List<DentalChartTooth> teeth = new ArrayList<>();
        for (String toothNumber : toothIdentifiersFor(chartType)) {
            teeth.add(
                    DentalChartTooth.builder()
                            .chartId(newChart.getId())
                            .toothNumber(toothNumber)
                            .missing(false)
                            .build()
            );
        }
        dentalChartToothRepository.saveAll(teeth);
        dentalChartToothRepository.flush();

        List<DentalChart> previousClosedCharts = dentalChartRepository.findMostRecentClosedEncounterChart(
                patient.getId(),
                newChart.getId(),
                PageRequest.of(0, 1)
        );

        if (!previousClosedCharts.isEmpty()) {
            cloneFromSourceChart(previousClosedCharts.get(0), newChart);
        }

        return assembleChart(newChart);
    }

    private void cloneFromSourceChart(DentalChart sourceChart, DentalChart newChart) {
        LOG.info(
                "Cloning dental chart data. sourceChartId={}, newChartId={}",
                sourceChart.getId(),
                newChart.getId()
        );

        dentalChartToothRepository.deleteByChartId(newChart.getId());

        List<DentalChartTooth> sourceTeeth = dentalChartToothRepository.findByChartId(sourceChart.getId());

        for (DentalChartTooth sourceTooth : sourceTeeth) {
            DentalChartTooth newTooth = DentalChartTooth.builder()
                    .chartId(newChart.getId())
                    .toothNumber(sourceTooth.getToothNumber())
                    .missing(sourceTooth.isMissing())
                    .build();
            newTooth = dentalChartToothRepository.saveAndFlush(newTooth);

            List<ToothAction> sourceActions = toothActionRepository.findByToothIdAndCancelledFalse(sourceTooth.getId());
            for (ToothAction sourceAction : sourceActions) {
                ToothAction clonedAction = ToothAction.builder()
                        .toothId(newTooth.getId())
                        .dentalActionId(sourceAction.getDentalActionId())
                        .note(sourceAction.getNote())
                        .surface(sourceAction.getSurface())
                        .existing(sourceAction.isExisting())
                        .imageName(sourceAction.getImageName())
                        .toothNumber(sourceAction.getToothNumber())
                        .cancelled(false)
                        .build();
                toothActionRepository.save(clonedAction);
            }

            List<ToothActionLog> sourceLogs = toothActionLogRepository.findByToothIdOrderByLogTimeDesc(sourceTooth.getId());
            for (ToothActionLog sourceLog : sourceLogs) {
                ToothActionLog clonedLog = ToothActionLog.builder()
                        .toothId(newTooth.getId())
                        .dentalActionId(sourceLog.getDentalActionId())
                        .logType(sourceLog.getLogType())
                        .logTime(sourceLog.getLogTime())
                        .logOwner(sourceLog.getLogOwner())
                        .build();
                toothActionLogRepository.save(clonedLog);
            }
        }

        List<DentalChartProgressNote> sourceNotes =
                dentalChartProgressNoteRepository.findByChartIdAndDeletedAtIsNullOrderByCreatedDateDesc(sourceChart.getId());
        for (DentalChartProgressNote sourceNote : sourceNotes) {
            DentalChartProgressNote clonedNote = DentalChartProgressNote.builder()
                    .chartId(newChart.getId())
                    .note(sourceNote.getNote())
                    .build();
            dentalChartProgressNoteRepository.save(clonedNote);
        }
    }

    private DentalChartResponseDTO assembleChart(DentalChart chart) {
        List<DentalChartTooth> teeth = dentalChartToothRepository.findByChartId(chart.getId());

        List<DentalChartToothDTO> toothDTOs = new ArrayList<>();
        List<ToothActionDTO> chartActions = new ArrayList<>();

        for (DentalChartTooth tooth : teeth) {
            List<ToothActionDTO> actions = toothActionRepository.findByToothIdAndCancelledFalse(tooth.getId())
                    .stream()
                    .map(ToothActionDTO::fromEntity)
                    .toList();
            List<ToothActionLogDTO> history = toothActionLogRepository.findByToothIdOrderByLogTimeDesc(tooth.getId())
                    .stream()
                    .map(ToothActionLogDTO::fromEntity)
                    .toList();
            List<ToothServiceDTO> services = toothServiceRepository.findByToothIdAndCancelledFalse(tooth.getId())
                    .stream()
                    .map(ToothServiceDTO::fromEntity)
                    .toList();
            List<ToothCdtDTO> cdts = toothCdtRepository.findByToothIdAndCancelledFalse(tooth.getId())
                    .stream()
                    .map(ToothCdtDTO::fromEntity)
                    .toList();

            toothDTOs.add(DentalChartToothDTO.fromEntity(tooth, actions, history, services, cdts));
            chartActions.addAll(actions);
        }

        List<DentalChartSummaryDTO> historyCharts = dentalChartRepository
                .findByPatient_IdAndIdNotOrderByCreatedDateDesc(chart.getPatient().getId(), chart.getId())
                .stream()
                .map(DentalChartSummaryDTO::fromEntity)
                .toList();

        return new DentalChartResponseDTO(
                chart.getId(),
                chart.getPatient().getId(),
                chart.getEncounter().getId(),
                chart.getChartDate(),
                chart.getChartType(),
                chart.getStatus(),
                chart.getTreatmentPlan(),
                toothDTOs,
                chartActions,
                historyCharts
        );
    }

    private DentalChartType resolveChartType(Patient patient) {
        LocalDate dob = patient.getDateOfBirth();
        if (dob == null) {
            return DentalChartType.ADULT;
        }
        int age = Period.between(dob, LocalDate.now()).getYears();
        return age < CHILD_AGE_THRESHOLD ? DentalChartType.CHILD : DentalChartType.ADULT;
    }

    private List<String> toothIdentifiersFor(DentalChartType chartType) {
        if (chartType == DentalChartType.CHILD) {
            return Arrays.stream(PrimaryToothNumber.values()).map(Enum::name).toList();
        }
        return Arrays.stream(ToothNumber.values()).map(Enum::name).toList();
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "encounterNotFound",
                        "dentalChart",
                        "Encounter not found with id " + encounterId
                ));
    }

    private DentalChart getChart(Long chartId) {
        return dentalChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart not found: " + chartId,
                        "dentalChart",
                        "notfound"
                ));
    }
}
