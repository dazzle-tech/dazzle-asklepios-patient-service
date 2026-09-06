package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApprovalSupportingInfoMapper {

    private static final String ACTIVE = "ACTIVE";
    private static final String INVESTIGATION_RESULT = "investigation-result";
    private static final String INVESTIGATION_NOT_PERFORMED = "INP";
    private static final String INVESTIGATION_RESULTS_PENDING = "IRP";
    private static final String INVESTIGATION_NOT_APPLICABLE = "NA";

    private final ChiefComplainRepository chiefComplainRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final ProgressNoteRepository progressNoteRepository;
    private final EncounterAssessmentRepository encounterAssessmentRepository;
    private final PatientProblemRepository patientProblemRepository;
    private final SurgicalHistoryRepository surgicalHistoryRepository;
    private final SocialHistoryRepository socialHistoryRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;

    public List<WaseelApprovalSupportingInfo> toSupportingInfo(PatientEncounter encounter) {
        if (encounter == null || encounter.getId() == null) {
            return List.of();
        }

        List<WaseelApprovalSupportingInfo> result = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);

        Long encounterId = encounter.getId();
        Long patientId = encounter.getPatient() == null ? null : encounter.getPatient().getId();

        ChiefComplain chief = chiefComplainRepository
                .findTopByEncounter_IdOrderByIdDesc(encounterId)
                .orElse(null);

        addClinicalTextIfExists(result, sequence, encounter, chief, encounterId, patientId);
        addVitalSignsIfExists(result, sequence, encounter);
        addBodyMeasurementsIfExists(result, sequence, encounter);

        return result;
    }

    private void addClinicalTextIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter,
            ChiefComplain chief,
            Long encounterId,
            Long patientId
    ) {
        String reasonOfVisit = patientObservationsComplaintsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .map(PatientObservationsComplaints::getReasonOfVisit)
                .filter(this::isNotBlank)
                .map(String::trim)
                .orElse(null);

        String progressNote = progressNoteRepository
                .findByEncounterIdAndCancelledDateIsNull(encounterId, PageRequest.of(0, 1))
                .stream()
                .map(ProgressNote::getNoteText)
                .filter(this::isNotBlank)
                .map(String::trim)
                .findFirst()
                .orElse(null);

        String assessment = encounterAssessmentRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId)
                .map(EncounterAssessment::getAssessment)
                .filter(this::isNotBlank)
                .map(String::trim)
                .orElse(null);

        String patientProblems = patientId == null
                ? null
                : patientProblemRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 100))
                .stream()
                .filter(problem -> problem.getStatus() != null)
                .filter(problem -> ACTIVE.equalsIgnoreCase(problem.getStatus().name()))
                .map(PatientProblem::getCondition)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));

        String surgicalHistory = patientId == null
                ? null
                : surgicalHistoryRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 100))
                .stream()
                .map(SurgicalHistory::getSurgery)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));

        String socialHistory = patientId == null
                ? null
                : socialHistoryRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 1))
                .stream()
                .map(this::mapSocialHistory)
                .filter(this::isNotBlank)
                .map(String::trim)
                .findFirst()
                .orElse(null);

        patientProblems = blankToNull(patientProblems);
        surgicalHistory = blankToNull(surgicalHistory);

        addTextIfExists(
                result,
                sequence,
                "chief-complaint",
                firstNonBlank(
                        encounter.getChiefComplaint(),
                        chief == null ? null : chief.getChiefComplaint(),
                        reasonOfVisit
                )
        );

        addTextIfExists(
                result,
                sequence,
                "history-of-present-illness",
                firstNonBlank(
                        chief == null ? null : chief.getCaseUnderstanding(),
                        progressNote,
                        reasonOfVisit,
                        encounter.getChiefComplaint()
                )
        );

        addTextIfExists(
                result,
                sequence,
                "patient-history",
                firstNonBlank(
                        encounter.getNotes(),
                        patientProblems,
                        socialHistory,
                        surgicalHistory
                )
        );

        addTextIfExists(
                result,
                sequence,
                "physical-examination",
                firstNonBlank(
                        encounter.getPhysicalExaminationSummery(),
                        assessment
                )
        );

        addInvestigationResult(result, sequence, encounterId);

        addTextIfExists(
                result,
                sequence,
                "treatment-plan",
                firstNonBlank(
                        assessment,
                        progressNote
                )
        );
    }

    private void addInvestigationResult(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        List<DiagnosticOrderTest> tests = findActiveInvestigationTests(encounterId);
        String investigationValue = collectLabInvestigationResultValue(tests);

        if (isNotBlank(investigationValue)) {
            result.add(codeAndValueInfo(
                    sequence,
                    INVESTIGATION_RESULT,
                    INVESTIGATION_NOT_APPLICABLE,
                    investigationValue
            ));
            return;
        }

        if (tests.isEmpty()) {
            result.add(codeInfo(sequence, INVESTIGATION_RESULT, INVESTIGATION_NOT_PERFORMED));
            return;
        }

        result.add(codeInfo(sequence, INVESTIGATION_RESULT, INVESTIGATION_RESULTS_PENDING));
    }

    private List<DiagnosticOrderTest> findActiveInvestigationTests(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }

        List<Long> orderIds = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED)
                .stream()
                .map(DiagnosticOrder::getId)
                .filter(Objects::nonNull)
                .toList();

        if (orderIds.isEmpty()) {
            return List.of();
        }

        return diagnosticOrderTestRepository.findByOrderIdInAndStatusNot(
                orderIds,
                DiagnosticOrderTestStatus.CANCELLED
        );
    }

    private String collectLabInvestigationResultValue(List<DiagnosticOrderTest> tests) {
        List<Long> labOrderTestIds = tests.stream()
                .filter(this::isLabInvestigationTest)
                .map(DiagnosticOrderTest::getId)
                .filter(Objects::nonNull)
                .toList();

        if (labOrderTestIds.isEmpty()) {
            return null;
        }

        List<String> values = diagnosticOrderTestResultRepository.findByOrderTestIdIn(labOrderTestIds).stream()
                .filter(this::isUsableInvestigationResult)
                .map(this::formatLabResultValue)
                .filter(this::isNotBlank)
                .toList();

        return values.isEmpty() ? null : String.join("; ", values);
    }

    private boolean isLabInvestigationTest(DiagnosticOrderTest test) {
        if (test == null || test.getOrderType() == null) {
            return false;
        }

        return test.getOrderType() == TestType.LABORATORY
                || test.getOrderType() == TestType.PATHOLOGY
                || test.getOrderType() == TestType.MICROBIOLOGY;
    }

    private boolean isUsableInvestigationResult(DiagnosticOrderTestResult source) {
        return source != null && !isExcludedInvestigationStatus(source.getProcessingStatus());
    }

    private boolean isExcludedInvestigationStatus(DiagnosticStatus status) {
        return status == DiagnosticStatus.CANCELLED || status == DiagnosticStatus.RESULT_REJECTED;
    }

    private String formatLabResultValue(DiagnosticOrderTestResult source) {
        if (source == null) {
            return null;
        }

        String number = cleanNumber(source.getResultValueNumber());
        String text = clean(source.getResultValueText());

        if (isNotBlank(number) && isNotBlank(text)) {
            return number + " " + text;
        }

        return firstNonBlank(number, text);
    }

    private void addVitalSignsIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter
    ) {
        if (encounter == null || encounter.getId() == null) {
            return;
        }

        LocalDate date = resolveDate(encounter);

        vitalSignsRepository
                .findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounter.getId())
                .ifPresent(vital -> {
                    addNumberIfExists(result, sequence, "pulse", vital.getHeartRate(), "/min", date);
                    addNumberIfExists(result, sequence, "temperature", vital.getTemperature(), "Cel", date);
                    addNumberIfExists(result, sequence, "respiratory-rate", vital.getRespiratoryRate(), "/min", date);
                    addNumberIfExists(result, sequence, "oxygen-saturation", vital.getOxygenSaturation(), "%", date);
                    addNumberIfExists(result, sequence, "vital-sign-systolic", vital.getBloodPressureSystolic(), "mm[Hg]", date);
                    addNumberIfExists(result, sequence, "vital-sign-diastolic", vital.getBloodPressureDiastolic(), "mm[Hg]", date);
                });
    }

    private void addBodyMeasurementsIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter
    ) {
        if (encounter == null || encounter.getId() == null) {
            return;
        }

        LocalDate date = resolveDate(encounter);

        bodyMeasurementsRepository
                .findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounter.getId())
                .ifPresent(body -> {
                    addNumberIfExists(result, sequence, "vital-sign-height", body.getHeight(), "cm", date);
                    addNumberIfExists(result, sequence, "vital-sign-weight", body.getWeight(), "kg", date);
                });
    }

    public Integer addMedicationDaysSupply(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Integer daysSupply
    ) {
        if (daysSupply == null || daysSupply <= 0) {
            return null;
        }

        return addValueAndReturnSequence(
                result,
                sequence,
                "days-supply",
                String.valueOf(daysSupply),
                "d",
                null,
                null
        );
    }

    private Integer addValueAndReturnSequence(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            String category,
            String value,
            String unit,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Integer currentSequence = sequence.getAndIncrement();

        result.add(new WaseelApprovalSupportingInfo(
                currentSequence,
                category,
                null,
                fromDate == null ? null : fromDate.toString(),
                toDate == null ? null : toDate.toString(),
                clean(value),
                null,
                null,
                null,
                null,
                clean(unit),
                null
        ));

        return currentSequence;
    }

    private void addNumberIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            String category,
            Number value,
            String unit,
            LocalDate date
    ) {
        if (value == null) {
            return;
        }

        result.add(valueInfo(
                sequence,
                category,
                cleanNumber(value),
                unit,
                date,
                date
        ));
    }

    private String mapSocialHistory(SocialHistory source) {
        if (source == null) {
            return null;
        }

        List<String> values = new ArrayList<>();

        if (Boolean.TRUE.equals(source.getIsCurrentSmoker())) {
            values.add("Current smoker");
        }

        if (Boolean.TRUE.equals(source.getIsPreviousSmoker())) {
            values.add("Previous smoker");
        }

        if (Boolean.TRUE.equals(source.getAlcoholConsumption())) {
            values.add("Alcohol consumption");
        }

        if (Boolean.TRUE.equals(source.getSubstanceUse())) {
            values.add("Substance use");
        }

        if (isNotBlank(source.getPhysicalLimitation())) {
            values.add(source.getPhysicalLimitation().trim());
        }

        if (isNotBlank(source.getDiagnosedEatingDisorders())) {
            values.add(source.getDiagnosedEatingDisorders().trim());
        }

        return values.isEmpty() ? null : String.join(", ", values);
    }

    private void addTextIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            String category,
            String value
    ) {
        if (isNotBlank(value)) {
            result.add(textInfo(sequence, category, value));
        }
    }

    private WaseelApprovalSupportingInfo codeAndValueInfo(
            AtomicInteger sequence,
            String category,
            String code,
            String value
    ) {
        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                category,
                clean(code),
                null,
                null,
                clean(value),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private WaseelApprovalSupportingInfo textInfo(
            AtomicInteger sequence,
            String category,
            String value
    ) {
        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                category,
                null,
                null,
                null,
                clean(value),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private WaseelApprovalSupportingInfo codeInfo(
            AtomicInteger sequence,
            String category,
            String code
    ) {
        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                category,
                clean(code),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private WaseelApprovalSupportingInfo valueInfo(
            AtomicInteger sequence,
            String category,
            String value,
            String unit,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                category,
                null,
                fromDate == null ? null : fromDate.toString(),
                toDate == null ? null : toDate.toString(),
                clean(value),
                null,
                null,
                null,
                null,
                clean(unit),
                null
        );
    }
    private LocalDate resolveDate(PatientEncounter encounter) {
        return encounter != null && encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();
    }

    private String cleanNumber(Number value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.stripTrailingZeros().toPlainString();
        }

        return String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private String blankToNull(String value) {
        return isNotBlank(value) ? value.trim() : null;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}