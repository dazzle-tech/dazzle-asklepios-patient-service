package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.DentalChartAgeRuleClient;
import com.dazzle.asklepios.client.setup.dto.DentalChartAgeRuleDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartProgressNote;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.ToothNumberingSystem;
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
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class DentalChartService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalChartService.class);
    private static final String ENTITY_NAME = "dentalChart";

    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final DentalChartProgressNoteRepository dentalChartProgressNoteRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final DentalChartAgeRuleClient dentalChartAgeRuleClient;

    public DentalChartService(
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository,
            ToothCdtRepository toothCdtRepository,
            ToothServiceRepository toothServiceRepository,
            DentalChartProgressNoteRepository dentalChartProgressNoteRepository,
            PatientEncounterRepository patientEncounterRepository,
            DentalChartAgeRuleClient dentalChartAgeRuleClient
    ) {
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.dentalChartProgressNoteRepository = dentalChartProgressNoteRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.dentalChartAgeRuleClient = dentalChartAgeRuleClient;
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

    /**
     * Read the chart already stored for an encounter. Does not create teeth or a chart.
     */
    @Transactional(readOnly = true)
    public Optional<DentalChartResponseDTO> findExistingChartForEncounter(Long encounterId) {
        return dentalChartRepository.findByEncounter_Id(encounterId).map(this::assembleChart);
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
        DentalChartAgeRuleDTO rule = resolveActiveSetupRule(patient, encounter);

        DentalChartType chartType = parseChartType(rule.chartType());
        ToothNumberingSystem numberingSystem = parseNumberingSystem(rule.numberingSystem());
        List<String> toothNumbers = normalizeToothInventory(rule.toothNumbers());
        if (toothNumbers.isEmpty()) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule has no teeth selected. Configure teeth in Dental Chart Setup, then retry.",
                    ENTITY_NAME,
                    "setup.emptyTeeth"
            );
        }

        DentalChart newChart = DentalChart.builder()
                .patient(patient)
                .encounter(encounter)
                .chartType(chartType)
                .numberingSystem(numberingSystem)
                .ageRuleId(rule.id())
                .status(DentalChartStatus.NEW)
                .chartDate(Instant.now())
                .build();

        newChart = dentalChartRepository.saveAndFlush(newChart);

        List<DentalChartTooth> teeth = new ArrayList<>();
        for (String toothNumber : toothNumbers) {
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
            cloneClinicalDataOntoTemplate(previousClosedCharts.get(0), newChart);
        }

        return assembleChart(newChart);
    }

    /**
     * Copy clinical state from a prior chart onto matching teeth in the new template.
     * Does not delete or recreate template teeth, and does not restore teeth excluded by Setup.
     */
    private void cloneClinicalDataOntoTemplate(DentalChart sourceChart, DentalChart newChart) {
        LOG.info(
                "Cloning dental chart clinical data onto template. sourceChartId={}, newChartId={}",
                sourceChart.getId(),
                newChart.getId()
        );

        List<DentalChartTooth> templateTeeth = dentalChartToothRepository.findByChartId(newChart.getId());
        Map<String, DentalChartTooth> templateByNumber = new HashMap<>();
        for (DentalChartTooth tooth : templateTeeth) {
            templateByNumber.put(tooth.getToothNumber(), tooth);
        }

        List<DentalChartTooth> sourceTeeth = dentalChartToothRepository.findByChartId(sourceChart.getId());
        for (DentalChartTooth sourceTooth : sourceTeeth) {
            DentalChartTooth targetTooth = templateByNumber.get(sourceTooth.getToothNumber());
            if (targetTooth == null) {
                // Tooth was excluded by the active Setup inventory — do not restore it.
                continue;
            }

            targetTooth.setMissing(sourceTooth.isMissing());
            targetTooth = dentalChartToothRepository.saveAndFlush(targetTooth);

            List<ToothAction> sourceActions = toothActionRepository.findByToothIdAndCancelledFalse(sourceTooth.getId());
            for (ToothAction sourceAction : sourceActions) {
                ToothAction clonedAction = ToothAction.builder()
                        .toothId(targetTooth.getId())
                        .dentalActionId(sourceAction.getDentalActionId())
                        .note(sourceAction.getNote())
                        .surface(sourceAction.getSurface())
                        .existing(sourceAction.isExisting())
                        .imageName(sourceAction.getImageName())
                        .toothNumber(sourceAction.getToothNumber())
                        .anesthesiaUsed(sourceAction.getAnesthesiaUsed())
                        .dose(sourceAction.getDose())
                        .cancelled(false)
                        .build();
                toothActionRepository.save(clonedAction);
            }

            List<ToothActionLog> sourceLogs = toothActionLogRepository.findByToothIdOrderByLogTimeDesc(sourceTooth.getId());
            for (ToothActionLog sourceLog : sourceLogs) {
                ToothActionLog clonedLog = ToothActionLog.builder()
                        .toothId(targetTooth.getId())
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

    private DentalChartAgeRuleDTO resolveActiveSetupRule(Patient patient, PatientEncounter encounter) {
        LocalDate birthDate = patient.getDateOfBirth();
        if (birthDate == null) {
            throw new BadRequestAlertException(
                    "Patient date of birth is required to resolve dental chart setup. Add DOB or configure setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }

        LocalDate asOfDate = encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();

        try {
            ResponseEntity<DentalChartAgeRuleDTO> response =
                    dentalChartAgeRuleClient.resolveByBirthDate(birthDate, asOfDate);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BadRequestAlertException(
                        "No active dental chart setup rule matches this patient's age at the encounter date. Configure Dental Chart Setup, then retry.",
                        ENTITY_NAME,
                        "setup.unresolved"
                );
            }
            return response.getBody();
        } catch (BadRequestAlertException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            throw new BadRequestAlertException(
                    "No active dental chart setup rule matches this patient's age at the encounter date. Configure Dental Chart Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        } catch (FeignException ex) {
            LOG.error(
                    "Failed to resolve dental chart age rule birthDate={} asOfDate={} status={}",
                    birthDate,
                    asOfDate,
                    ex.status(),
                    ex
            );
            throw new BadRequestAlertException(
                    "Dental chart setup could not be reached. Check Setup service and retry.",
                    ENTITY_NAME,
                    "setup.unavailable"
            );
        } catch (Exception ex) {
            LOG.error("Unexpected error resolving dental chart age rule", ex);
            throw new BadRequestAlertException(
                    "Dental chart setup could not be resolved. Please retry.",
                    ENTITY_NAME,
                    "setup.unavailable"
            );
        }
    }

    private static DentalChartType parseChartType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule is missing chart type. Fix the rule in Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }
        try {
            return DentalChartType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule has an invalid chart type. Fix the rule in Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }
    }

    private static ToothNumberingSystem parseNumberingSystem(String value) {
        if (value == null || value.isBlank()) {
            return ToothNumberingSystem.UNIVERSAL;
        }
        try {
            return ToothNumberingSystem.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ToothNumberingSystem.UNIVERSAL;
        }
    }

    private static List<String> normalizeToothInventory(List<String> toothNumbers) {
        if (toothNumbers == null || toothNumbers.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String raw : toothNumbers) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (id.isEmpty() || !seen.add(id)) {
                continue;
            }
            ordered.add(id);
        }
        return List.copyOf(ordered);
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

        ToothNumberingSystem numberingSystem = chart.getNumberingSystem() != null
                ? chart.getNumberingSystem()
                : ToothNumberingSystem.UNIVERSAL;

        return new DentalChartResponseDTO(
                chart.getId(),
                chart.getPatient().getId(),
                chart.getEncounter().getId(),
                chart.getChartDate(),
                chart.getChartType(),
                chart.getStatus(),
                chart.getTreatmentPlan(),
                numberingSystem,
                chart.getAgeRuleId(),
                toothDTOs,
                chartActions,
                historyCharts
        );
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounterNotFound"
                ));
    }

    private DentalChart getChart(Long chartId) {
        return dentalChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart not found: " + chartId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }
}
