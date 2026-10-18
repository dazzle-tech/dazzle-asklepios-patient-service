package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterProgressNote;
import com.dazzle.asklepios.domain.ClaimEncounterCopyCurrentMedication;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.ClaimEncounterCopyFamilyHistory;
import com.dazzle.asklepios.domain.ClaimEncounterCopyHospitalization;
import com.dazzle.asklepios.domain.ClaimEncounterCopyPatientProblem;
import com.dazzle.asklepios.domain.ClaimEncounterCopySocialHistory;
import com.dazzle.asklepios.domain.ClaimEncounterCopySurgicalHistory;
import com.dazzle.asklepios.domain.Department;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticTest;
import com.dazzle.asklepios.domain.DiagnosticTestLaboratory;
import com.dazzle.asklepios.domain.DiagnosticTestProfile;
import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.client.setup.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.NormalRangeType;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.service.NormalRangeMatcherService;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsPdfRenderer;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsReport;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyCurrentMedicationRepository;
import com.dazzle.asklepios.repository.ClaimEncounterProgressNoteRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyFamilyHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyHospitalizationRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyPatientProblemRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySocialHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySurgicalHistoryRepository;
import com.dazzle.asklepios.repository.DepartmentRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticTestLaboratoryRepository;
import com.dazzle.asklepios.repository.DiagnosticTestProfileRepository;
import com.dazzle.asklepios.repository.DiagnosticTestRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSupportingInfoMapper {

    private static final String ACTIVE = "ACTIVE";
    private static final String INVESTIGATION_RESULT = "investigation-result";
    private static final String INVESTIGATION_NOT_PERFORMED = "INP";
    private static final String INVESTIGATION_RESULTS_PENDING = "IRP";
    private static final String INVESTIGATION_RESULTS_ATTACHED = "IRA";
    private final int maxReportTextLength = 8000;
    private final Pattern htmlMarkup = Pattern.compile("(?i)</?(p|br|div|span|li|ul|ol|b|i|u|strong|em|h[1-6]|table|tr|td)\\b[^>]*>");

    private final ChiefComplainRepository chiefComplainRepository;
    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;
    private final ClaimEncounterProgressNoteRepository claimEncounterProgressNoteRepository;
    private final ClaimEncounterCopyPatientProblemRepository claimEncounterCopyPatientProblemRepository;
    private final ClaimEncounterCopySurgicalHistoryRepository claimEncounterCopySurgicalHistoryRepository;
    private final ClaimEncounterCopySocialHistoryRepository claimEncounterCopySocialHistoryRepository;
    private final ClaimEncounterCopyFamilyHistoryRepository claimEncounterCopyFamilyHistoryRepository;
    private final ClaimEncounterCopyHospitalizationRepository claimEncounterCopyHospitalizationRepository;
    private final ClaimEncounterCopyCurrentMedicationRepository claimEncounterCopyCurrentMedicationRepository;
    private final ClaimEncounterCopyDiagnosticOrderTestResultRepository claimEncounterCopyDiagnosticOrderTestResultRepository;
    private final ClaimEncounterCopyDiagnosticOrderTestReportRepository claimEncounterCopyDiagnosticOrderTestReportRepository;
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
    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;
    private final DiagnosticTestRepository diagnosticTestRepository;
    private final DiagnosticTestProfileRepository diagnosticTestProfileRepository;
    private final DiagnosticTestLaboratoryRepository diagnosticTestLaboratoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ApLovValueRepository apLovValueRepository;
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final InvestigationResultsPdfRenderer investigationResultsPdfRenderer;

    public List<WaseelApprovalSupportingInfo> toSupportingInfo(PatientEncounter encounter) {
        return toSupportingInfo(encounter, false);
    }

    /**
     * Same as {@link #toSupportingInfo(PatientEncounter)}, but history and the investigation-result
     * attachment are read from the claim encounter copy when one exists for the encounter.
     */
    public List<WaseelApprovalSupportingInfo> toClaimSupportingInfo(PatientEncounter encounter) {
        return toSupportingInfo(encounter, true);
    }

    private List<WaseelApprovalSupportingInfo> toSupportingInfo(PatientEncounter encounter, boolean attachPdfReport) {
        if (encounter == null || encounter.getId() == null) {
            return List.of();
        }

        List<WaseelApprovalSupportingInfo> result = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);

        Long encounterId = encounter.getId();
        Long patientId = encounter.getPatient() == null ? null : encounter.getPatient().getId();
        ClaimEncounterCopy claimCopy = attachPdfReport
                ? claimEncounterCopyRepository.findByEncounterId(encounterId).orElse(null)
                : null;

        ChiefComplain chief = claimCopy == null
                ? chiefComplainRepository.findTopByEncounter_IdOrderByIdDesc(encounterId).orElse(null)
                : null;

        addClinicalTextIfExists(result, sequence, encounter, chief, encounterId, patientId, attachPdfReport, claimCopy);
        if (claimCopy != null) {
            addClaimCopyMeasurements(result, sequence, encounter, claimCopy);
        } else {
            addVitalSignsIfExists(result, sequence, encounter);
            addBodyMeasurementsIfExists(result, sequence, encounter);
        }

        return result;
    }

    private void addClinicalTextIfExists(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter,
            ChiefComplain chief,
            Long encounterId,
            Long patientId,
            boolean attachPdfReport,
            ClaimEncounterCopy claimCopy
    ) {
        if (claimCopy != null) {
            addClaimCopyClinicalText(result, sequence, encounter, claimCopy, attachPdfReport);
            return;
        }

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

        String patientProblems = blankToNull(livePatientProblems(patientId));
        String surgicalHistory = blankToNull(liveSurgicalHistory(patientId));
        String socialHistory = liveSocialHistory(patientId);

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

        addInvestigationResult(result, sequence, encounter, attachPdfReport, null);

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

    /**
     * Claim supporting info uses the edited encounter copy: supportive-info fields, progress notes,
     * and patient-history tables. Live visit text is not mixed in.
     */
    private void addClaimCopyClinicalText(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter,
            ClaimEncounterCopy claimCopy,
            boolean attachPdfReport
    ) {
        Long claimCopyId = claimCopy.getId();
        String progressNotes = copyProgressNotes(claimCopyId);

        addTextIfExists(result, sequence, "chief-complaint", clean(claimCopy.getChiefComplaint()));
        addTextIfExists(
                result,
                sequence,
                "history-of-present-illness",
                joinDistinctNonBlank("; ", clean(claimCopy.getHistoryOfPresentIllness()), progressNotes)
        );
        addTextIfExists(
                result,
                sequence,
                "patient-history",
                joinDistinctNonBlank(
                        "; ",
                        copyPatientProblems(claimCopyId),
                        copySocialHistory(claimCopyId),
                        copySurgicalHistory(claimCopyId),
                        copyFamilyHistory(claimCopyId),
                        copyHospitalizations(claimCopyId),
                        copyCurrentMedications(claimCopyId)
                )
        );
        addTextIfExists(
                result,
                sequence,
                "physical-examination",
                joinDistinctNonBlank("; ", clean(claimCopy.getPhysicalExamination()), clean(claimCopy.getAssessment()))
        );

        addInvestigationResult(result, sequence, encounter, attachPdfReport, claimCopyId);

        addTextIfExists(
                result,
                sequence,
                "treatment-plan",
                firstNonBlank(clean(claimCopy.getTreatmentPlan()), clean(claimCopy.getAssessment()), progressNotes)
        );
    }

    private void addClaimCopyMeasurements(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter,
            ClaimEncounterCopy claimCopy
    ) {
        LocalDate date = resolveDate(encounter);
        addNumberIfExists(result, sequence, "pulse", claimCopy.getPulse(), "/min", date);
        addNumberIfExists(result, sequence, "temperature", claimCopy.getTemperature(), "Cel", date);
        addNumberIfExists(result, sequence, "respiratory-rate", claimCopy.getRespiratoryRate(), "/min", date);
        addNumberIfExists(result, sequence, "oxygen-saturation", claimCopy.getOxygenSaturation(), "%", date);
        addNumberIfExists(result, sequence, "vital-sign-systolic", claimCopy.getBloodPressureSystolic(), "mm[Hg]", date);
        addNumberIfExists(result, sequence, "vital-sign-diastolic", claimCopy.getBloodPressureDiastolic(), "mm[Hg]", date);
        addNumberIfExists(result, sequence, "vital-sign-height", claimCopy.getHeight(), "cm", date);
        addNumberIfExists(result, sequence, "vital-sign-weight", claimCopy.getWeight(), "kg", date);
    }

    private String copyProgressNotes(Long claimCopyId) {
        return joinDistinctNonBlank(
                "; ",
                claimEncounterProgressNoteRepository
                        .findByClaimEncounterCopyIdAndCancelledDateIsNull(claimCopyId)
                        .stream()
                        .map(ClaimEncounterProgressNote::getNoteText)
                        .toArray(String[]::new)
        );
    }

    private void addInvestigationResult(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter,
            boolean attachPdfReport,
            Long claimCopyId
    ) {
        Long encounterId = encounter == null ? null : encounter.getId();
        List<DiagnosticOrderTest> tests = findActiveInvestigationTests(encounterId);

        if (attachPdfReport) {
            WaseelApprovalSupportingInfo pdfInfo = investigationResultPdfAttached(sequence, encounter, tests, claimCopyId);
            if (pdfInfo != null) {
                result.add(pdfInfo);
                return;
            }
        }

        String investigationValue = collectLabInvestigationResultValue(tests, claimCopyId);

        if (isNotBlank(investigationValue)) {
            result.add(investigationResultAttached(sequence, investigationValue, resolveDate(encounter)));
            return;
        }

        if (tests.isEmpty()) {
            result.add(codeInfo(sequence, INVESTIGATION_RESULT, INVESTIGATION_NOT_PERFORMED));
            return;
        }

        result.add(codeInfo(sequence, INVESTIGATION_RESULT, INVESTIGATION_RESULTS_PENDING));
    }

    private WaseelApprovalSupportingInfo investigationResultAttached(
            AtomicInteger sequence,
            String investigationValue,
            LocalDate attachmentDate
    ) {
        String attachment = Base64.getEncoder().encodeToString(
                investigationValue.getBytes(StandardCharsets.UTF_8)
        );

        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                INVESTIGATION_RESULT,
                INVESTIGATION_RESULTS_ATTACHED,
                null,
                null,
                clean(investigationValue),
                null,
                attachment,
                "lab-investigation-results.txt",
                "text/plain",
                null,
                attachmentDate == null ? LocalDate.now().toString() : attachmentDate.toString()
        );
    }

    /**
     * Returns {@code null} when there are no results to report or the PDF cannot be produced,
     * so the caller keeps the existing investigation-result behaviour.
     * A PDF larger than 1 MB is attached as a zip of the same file.
     */
    private WaseelApprovalSupportingInfo investigationResultPdfAttached(
            AtomicInteger sequence,
            PatientEncounter encounter,
            List<DiagnosticOrderTest> tests,
            Long claimCopyId
    ) {
        if (tests.isEmpty()) {
            return null;
        }

        try {
            List<LabResultSource> labResults = findUsableLabResults(tests, claimCopyId);
            List<RadiologyReportSource> radiologyReports = findRadiologyReports(tests, claimCopyId);
            InvestigationResultsReport report = buildInvestigationReport(encounter, tests, labResults, radiologyReports);
            if (!report.hasResults()) {
                return null;
            }

            byte[] pdf = investigationResultsPdfRenderer.render(report);
            InvestigationResultsPdfRenderer.Attachment attachment = investigationResultsPdfRenderer.attachmentFor(pdf);
            if (!investigationResultsPdfRenderer.withinSizeLimit(pdf)) {
                log.info("Investigation results PDF exceeds 1MB and will be sent as a zip. encounterId={} pdfBytes={} zipBytes={}",
                        encounter.getId(), pdf.length, attachment.content().length);
            }

            String labValue = labResults.stream()
                    .map(this::formatLabResultValue)
                    .filter(this::isNotBlank)
                    .collect(Collectors.joining("; "));
            String radiologyValue = report.orders().stream()
                    .flatMap(order -> order.orderTests().stream())
                    .filter(section -> section.radiologyResults() != null && !section.radiologyResults().isEmpty())
                    .map(InvestigationResultsReport.OrderTestSection::testName)
                    .filter(this::isNotBlank)
                    .collect(Collectors.joining("; "));
            LocalDate attachmentDate = resolveDate(encounter);

            return new WaseelApprovalSupportingInfo(
                    sequence.getAndIncrement(),
                    INVESTIGATION_RESULT,
                    INVESTIGATION_RESULTS_ATTACHED,
                    null,
                    null,
                    firstNonBlank(labValue, radiologyValue),
                    null,
                    Base64.getEncoder().encodeToString(attachment.content()),
                    attachment.filename(),
                    attachment.contentType(),
                    null,
                    attachmentDate.toString()
            );
        } catch (RuntimeException e) {
            log.warn("Failed to build investigation results PDF. encounterId={}", encounter.getId(), e);
            return null;
        }
    }

    private InvestigationResultsReport buildInvestigationReport(
            PatientEncounter encounter,
            List<DiagnosticOrderTest> tests,
            List<LabResultSource> labResults,
            List<RadiologyReportSource> radiologyReports
    ) {
        Map<Long, DiagnosticOrderTest> testsById = tests.stream()
                .filter(test -> test.getId() != null)
                .collect(Collectors.toMap(DiagnosticOrderTest::getId, Function.identity(), (a, b) -> a));

        Map<Long, String> testNames = diagnosticTestRepository.findAllById(
                        tests.stream().map(DiagnosticOrderTest::getTestId).filter(Objects::nonNull).distinct().toList()
                ).stream()
                .filter(test -> test.getId() != null && isNotBlank(test.getName()))
                .collect(Collectors.toMap(DiagnosticTest::getId, test -> test.getName().trim(), (a, b) -> a));

        Map<Long, DiagnosticTestProfile> profiles = findProfiles(labResults);
        Map<Long, String> categories = findLabCategories(tests);
        Map<Long, NormalRangeMatchDTO> normalRanges = findNormalRanges(encounter, labResults);
        Map<String, String> lovLabels = findInvestigationLovLabels(profiles.values(), labResults, normalRanges);

        Map<Long, List<InvestigationResultsReport.LabResultRow>> labRowsByOrderTest = labResults.stream()
                .filter(source -> isNotBlank(formatLabResultValue(source)))
                .filter(source -> source.orderTestId() != null)
                .sorted(Comparator
                        .comparing(LabResultSource::orderTestId)
                        .thenComparing(LabResultSource::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(
                        LabResultSource::orderTestId,
                        LinkedHashMap::new,
                        Collectors.mapping(source -> {
                            DiagnosticOrderTest test = testsById.get(source.orderTestId());
                            DiagnosticTestProfile profile = source.profileTestId() == null
                                    ? null
                                    : profiles.get(source.profileTestId());
                            TestResultType resultType = resolveResultType(source, profile);
                            NormalRangeMatchDTO normalRange = source.profileTestId() == null
                                    ? null
                                    : normalRanges.get(source.profileTestId());
                            String unit = resolveUnitLabel(profile, resultType, lovLabels);
                            return new InvestigationResultsReport.LabResultRow(
                                    firstNonBlank(
                                            profile == null ? null : profile.getName(),
                                            resolveTestName(test, testNames)
                                    ),
                                    test == null || test.getTestId() == null ? null : categories.get(test.getTestId()),
                                    formatLabResultValue(source),
                                    unit,
                                    formatDisplayedNormalRange(source, resultType, normalRange, lovLabels, unit),
                                    markerLabel(source.marker()),
                                    isAbnormalMarker(source.marker()),
                                    firstNonNull(source.approvedDate(), source.reviewDate(), source.createdDate())
                            );
                        }, Collectors.toList())
                ));

        Map<Long, List<InvestigationResultsReport.RadiologyResult>> radiologyByOrderTest = radiologyReports.stream()
                .filter(source -> source.orderTestId() != null)
                .sorted(Comparator
                        .comparing(RadiologyReportSource::orderTestId)
                        .thenComparing(RadiologyReportSource::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(
                        RadiologyReportSource::orderTestId,
                        LinkedHashMap::new,
                        Collectors.mapping(source -> new InvestigationResultsReport.RadiologyResult(
                                humanize(source.processingStatus()),
                                severityLabel(source.severity()),
                                firstNonNull(source.approvedDate(), source.secondApprovedDate(),
                                        source.reviewDate(), source.createdDate()),
                                toPlainText(source.report()),
                                toPlainText(source.criticalFindings()),
                                toPlainText(source.radiologistComments()),
                                toPlainText(source.radiologistInformation()),
                                clean(source.reviewBy()),
                                firstNonBlank(clean(source.approvedBy()), clean(source.secondApprovedBy()))
                        ), Collectors.toList())
                ));

        Map<Long, DiagnosticOrder> ordersById = diagnosticOrderRepository.findAllById(
                        tests.stream().map(DiagnosticOrderTest::getOrderId).filter(Objects::nonNull).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(DiagnosticOrder::getId, Function.identity(), (a, b) -> a));

        Map<Long, String> departmentNames = findDepartmentNames(Stream.concat(
                ordersById.values().stream().map(DiagnosticOrder::getFromDepartmentId),
                tests.stream().map(DiagnosticOrderTest::getReceivedDepartmentId)
        ));

        String encounterNumber = firstNonBlank(
                encounter.getEncounterNumber(),
                encounter.getId() == null ? null : String.valueOf(encounter.getId())
        );

        Map<Long, List<InvestigationResultsReport.OrderTestSection>> sectionsByOrder = tests.stream()
                .filter(test -> test.getId() != null)
                .sorted(Comparator
                        .comparing(DiagnosticOrderTest::getOrderId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DiagnosticOrderTest::getId))
                .map(test -> Map.entry(
                        test.getOrderId() == null ? -1L : test.getOrderId(),
                        new InvestigationResultsReport.OrderTestSection(
                                resolveTestName(test, testNames),
                                test.getReceivedDepartmentId() == null
                                        ? null
                                        : departmentNames.get(test.getReceivedDepartmentId()),
                                labRowsByOrderTest.getOrDefault(test.getId(), List.of()),
                                radiologyByOrderTest.getOrDefault(test.getId(), List.of())
                        )
                ))
                .filter(entry -> entry.getValue().hasResults())
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        List<InvestigationResultsReport.OrderSection> orders = sectionsByOrder.entrySet().stream()
                .map(entry -> {
                    DiagnosticOrder order = ordersById.get(entry.getKey());
                    return new InvestigationResultsReport.OrderSection(
                            order == null ? null : firstNonBlank(order.getOrderNumber(), String.valueOf(order.getId())),
                            encounterNumber,
                            order == null || order.getFromDepartmentId() == null
                                    ? null
                                    : departmentNames.get(order.getFromDepartmentId()),
                            entry.getValue()
                    );
                })
                .toList();

        Patient patient = encounter.getPatient();

        return new InvestigationResultsReport(
                patient == null ? null : patientFullName(patient),
                patient == null ? null : clean(patient.getMedicalRecordNumber()),
                patient == null ? null : patient.getDateOfBirth(),
                patient == null ? null : humanize(patient.getSexAtBirth()),
                encounterNumber,
                encounter.getEncounterDate(),
                orders
        );
    }

    private Map<Long, DiagnosticTestProfile> findProfiles(List<LabResultSource> labResults) {
        List<Long> profileIds = labResults.stream()
                .map(LabResultSource::profileTestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        return diagnosticTestProfileRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(DiagnosticTestProfile::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * Best normal range per profile, using the patient already on the encounter.
     * A lookup failure leaves that profile without a computed range; the saved value is used instead.
     */
    private Map<Long, NormalRangeMatchDTO> findNormalRanges(
            PatientEncounter encounter,
            List<LabResultSource> labResults
    ) {
        Patient patient = encounter.getPatient();
        if (patient == null) {
            return Map.of();
        }

        Map<Long, NormalRangeMatchDTO> ranges = new HashMap<>();
        for (Long profileId : labResults.stream()
                .map(LabResultSource::profileTestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {
            try {
                NormalRangeMatchDTO match = normalRangeMatcherService.findBestNormalRange(profileId, patient);
                if (match != null) {
                    ranges.put(profileId, match);
                }
            } catch (RuntimeException ex) {
                log.warn("Unable to resolve normal range for profileTestId={}", profileId, ex);
            }
        }
        return ranges;
    }

    /**
     * Unit and LOV results are stored as LOV keys. Labels are resolved with the existing key lookup.
     */
    private Map<String, String> findInvestigationLovLabels(
            Collection<DiagnosticTestProfile> profiles,
            List<LabResultSource> labResults,
            Map<Long, NormalRangeMatchDTO> normalRanges
    ) {
        List<String> keys = new ArrayList<>();
        for (DiagnosticTestProfile profile : profiles) {
            if (profile != null && isNotBlank(profile.getResultUnit())) {
                keys.add(profile.getResultUnit().trim());
            }
        }
        for (LabResultSource result : labResults) {
            addLovKeys(keys, result.normalRangeValue());
            NormalRangeMatchDTO match = result.profileTestId() == null
                    ? null
                    : normalRanges.get(result.profileTestId());
            if (match != null && match.lovKeys() != null) {
                match.lovKeys().stream().filter(this::isNotBlank).map(String::trim).forEach(keys::add);
            }
        }

        List<String> distinct = keys.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return apLovValueRepository.findByKeyIn(distinct).stream()
                .filter(value -> value.getKey() != null && isNotBlank(value.getLovDisplayVale()))
                .collect(Collectors.toMap(ApLovValue::getKey, value -> value.getLovDisplayVale().trim(), (a, b) -> a));
    }

    private void addLovKeys(List<String> keys, String raw) {
        if (!isNotBlank(raw)) {
            return;
        }
        for (String key : raw.split(",")) {
            if (isNotBlank(key)) {
                keys.add(key.trim());
            }
        }
    }

    private TestResultType resolveResultType(LabResultSource result, DiagnosticTestProfile profile) {
        if (result != null && result.resultTypeAtEntry() != null) {
            return result.resultTypeAtEntry();
        }
        return profile == null ? null : profile.getResultType();
    }

    /**
     * LOV profiles have no unit. Numeric and text profiles show the VALUE_UNIT label, never the stored key.
     */
    private String resolveUnitLabel(
            DiagnosticTestProfile profile,
            TestResultType resultType,
            Map<String, String> lovLabels
    ) {
        if (resultType == TestResultType.LOV || profile == null || !isNotBlank(profile.getResultUnit())) {
            return null;
        }
        return lovLabels.get(profile.getResultUnit().trim());
    }

    /**
     * Matches the results screen: text has no range, LOV shows its labels, numbers show the bound plus unit.
     * Bound shape follows the test normal-range type (range, less than, more than).
     */
    private String formatDisplayedNormalRange(
            LabResultSource result,
            TestResultType resultType,
            NormalRangeMatchDTO match,
            Map<String, String> lovLabels,
            String unit
    ) {
        if (resultType == TestResultType.TEXT) {
            return null;
        }

        String view = formatViewNormalRange(match, resultType);
        if (!isNotBlank(view)) {
            view = clean(result.normalRangeValue());
        }
        if (!isNotBlank(view)) {
            return null;
        }
        if (resultType == TestResultType.LOV) {
            return resolveLovList(view, lovLabels);
        }
        if (resultType == TestResultType.NUMBER && isNotBlank(unit) && !view.endsWith(unit)) {
            return view + " " + unit;
        }
        return view;
    }

    private String formatViewNormalRange(NormalRangeMatchDTO match, TestResultType resultType) {
        if (match == null || resultType == null) {
            return null;
        }
        return switch (resultType) {
            case TEXT -> null;
            case LOV -> match.lovKeys() == null || match.lovKeys().isEmpty()
                    ? null
                    : match.lovKeys().stream()
                    .filter(this::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.joining(", "));
            case NUMBER -> formatNumericRange(match);
        };
    }

    private String formatNumericRange(NormalRangeMatchDTO match) {
        Double from = match.rangeFrom();
        Double to = match.rangeTo();
        NormalRangeType type = match.normalRangeType() == null ? NormalRangeType.RANGE : match.normalRangeType();
        return switch (type) {
            case RANGE -> {
                if (from != null && to != null) {
                    yield from + " - " + to;
                }
                if (from != null) {
                    yield ">= " + from;
                }
                if (to != null) {
                    yield "<= " + to;
                }
                yield null;
            }
            case LESS_THAN -> to == null ? null : "< " + to;
            case MORE_THAN -> from == null ? null : "> " + from;
        };
    }

    private String resolveLovList(String raw, Map<String, String> lovLabels) {
        String labels = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(this::isNotBlank)
                .map(key -> lovLabels.getOrDefault(key, key))
                .collect(Collectors.joining(", "));
        return blankToNull(labels);
    }

    /**
     * Lab category is stored as a LAB_CATEGORIES LOV key; resolved to its display value when possible.
     */
    private Map<Long, String> findLabCategories(List<DiagnosticOrderTest> tests) {
        List<Long> labTestIds = tests.stream()
                .filter(this::isLabInvestigationTest)
                .map(DiagnosticOrderTest::getTestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (labTestIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> rawCategories = new HashMap<>();
        for (DiagnosticTestLaboratory laboratory : diagnosticTestLaboratoryRepository.findByTest_IdIn(labTestIds)) {
            DiagnosticTest test = laboratory.getTest();
            if (test == null || test.getId() == null || !isNotBlank(laboratory.getCategory())) {
                continue;
            }
            rawCategories.putIfAbsent(test.getId(), laboratory.getCategory().trim());
        }
        if (rawCategories.isEmpty()) {
            return Map.of();
        }

        Map<String, String> displayByKey = apLovValueRepository
                .findByKeyIn(rawCategories.values().stream().distinct().toList()).stream()
                .filter(value -> value.getKey() != null && isNotBlank(value.getLovDisplayVale()))
                .collect(Collectors.toMap(ApLovValue::getKey, value -> value.getLovDisplayVale().trim(), (a, b) -> a));

        Map<Long, String> categories = new HashMap<>();
        rawCategories.forEach((testId, category) ->
                categories.put(testId, displayByKey.getOrDefault(category, humanizeCode(category))));
        return categories;
    }

    private Map<Long, String> findDepartmentNames(Stream<Long> departmentIds) {
        List<Long> ids = departmentIds.filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(ids).stream()
                .filter(department -> department.getId() != null && isNotBlank(department.getName()))
                .collect(Collectors.toMap(Department::getId, department -> department.getName().trim(), (a, b) -> a));
    }

    private boolean isAbnormalMarker(TestResultMarker marker) {
        return marker != null && marker != TestResultMarker.NORMAL_MARKER && marker != TestResultMarker.UNKNOWN;
    }

    private String humanizeCode(String value) {
        if (!value.matches("[A-Z0-9_]+") || !value.matches(".*[A-Z].*")) {
            return value;
        }
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private List<LabResultSource> findUsableLabResults(List<DiagnosticOrderTest> tests, Long claimCopyId) {
        List<Long> labOrderTestIds = tests.stream()
                .filter(this::isLabInvestigationTest)
                .map(DiagnosticOrderTest::getId)
                .filter(Objects::nonNull)
                .toList();

        if (labOrderTestIds.isEmpty()) {
            return List.of();
        }

        if (claimCopyId != null) {
            Set<Long> laboratoryOrderTestIds = tests.stream()
                    .filter(test -> test != null && test.getOrderType() == TestType.LABORATORY)
                    .map(DiagnosticOrderTest::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<Long> otherLabOrderTestIds = labOrderTestIds.stream()
                    .filter(id -> !laboratoryOrderTestIds.contains(id))
                    .toList();

            List<LabResultSource> results = new ArrayList<>(claimEncounterCopyDiagnosticOrderTestResultRepository
                    .findByClaimEncounterCopyId(claimCopyId)
                    .stream()
                    .filter(source -> source.getOrderTestId() != null && laboratoryOrderTestIds.contains(source.getOrderTestId()))
                    .map(this::toLabResultSource)
                    .toList());

            if (!otherLabOrderTestIds.isEmpty()) {
                diagnosticOrderTestResultRepository.findByOrderTestIdIn(otherLabOrderTestIds).stream()
                        .filter(this::isUsableInvestigationResult)
                        .map(this::toLabResultSource)
                        .forEach(results::add);
            }
            return results;
        }

        return diagnosticOrderTestResultRepository.findByOrderTestIdIn(labOrderTestIds).stream()
                .filter(this::isUsableInvestigationResult)
                .map(this::toLabResultSource)
                .toList();
    }

    private List<RadiologyReportSource> findRadiologyReports(List<DiagnosticOrderTest> tests, Long claimCopyId) {
        List<Long> radiologyOrderTestIds = tests.stream()
                .filter(test -> test.getOrderType() == TestType.RADIOLOGY)
                .map(DiagnosticOrderTest::getId)
                .filter(Objects::nonNull)
                .toList();

        if (radiologyOrderTestIds.isEmpty()) {
            return List.of();
        }

        Set<Long> radiologyOrderTestIdSet = new HashSet<>(radiologyOrderTestIds);
        if (claimCopyId != null) {
            return claimEncounterCopyDiagnosticOrderTestReportRepository
                    .findByClaimEncounterCopyId(claimCopyId)
                    .stream()
                    .filter(source -> source.getOrderTestId() != null && radiologyOrderTestIdSet.contains(source.getOrderTestId()))
                    .map(this::toRadiologyReportSource)
                    .filter(this::hasRadiologyReportContent)
                    .toList();
        }

        return diagnosticOrderTestReportRepository.findByOrderTestIdIn(radiologyOrderTestIds).stream()
                .filter(Objects::nonNull)
                .filter(source -> source.getOrderTestId() != null)
                .filter(source -> !isExcludedRadiologyStatus(source.getProcessingStatus()))
                .filter(this::hasRadiologyReportContent)
                .map(this::toRadiologyReportSource)
                .toList();
    }

    private boolean hasRadiologyReportContent(DiagnosticOrderTestReport source) {
        return source != null && hasRadiologyReportContent(
                source.getReport(),
                source.getCriticalFindings(),
                source.getRadiologistComments(),
                source.getRadiologistInformation()
        );
    }

    private boolean hasRadiologyReportContent(RadiologyReportSource source) {
        return source != null && hasRadiologyReportContent(
                source.report(),
                source.criticalFindings(),
                source.radiologistComments(),
                source.radiologistInformation()
        );
    }

    private boolean hasRadiologyReportContent(
            String report,
            String criticalFindings,
            String radiologistComments,
            String radiologistInformation
    ) {
        return isNotBlank(report)
                || isNotBlank(criticalFindings)
                || isNotBlank(radiologistComments)
                || isNotBlank(radiologistInformation);
    }

    private boolean isExcludedRadiologyStatus(DiagnosticStatus status) {
        return status == DiagnosticStatus.CANCELLED
                || status == DiagnosticStatus.REJECTED
                || status == DiagnosticStatus.RESULT_REJECTED;
    }

    private String resolveTestName(DiagnosticOrderTest test, Map<Long, String> testNames) {
        if (test == null) {
            return null;
        }
        String name = test.getTestId() == null ? null : testNames.get(test.getTestId());
        return name != null ? name : "Test #" + test.getTestId();
    }

    /**
     * The PDF standard fonts only cover Latin-1, so a Latin-script name is preferred over an Arabic one.
     */
    private String patientFullName(Patient patient) {
        String primary = joinName(
                patient.getFirstName(),
                patient.getSecondName(),
                patient.getThirdName(),
                patient.getLastName()
        );
        String secondary = joinName(
                patient.getFirstNameSecondaryLang(),
                patient.getSecondNameSecondaryLang(),
                patient.getThirdNameSecondaryLang(),
                patient.getLastNameSecondaryLang()
        );
        if (primary != null && !isLatin1(primary) && secondary != null && isLatin1(secondary)) {
            return secondary;
        }
        return firstNonBlank(primary, secondary);
    }

    private String joinName(String... parts) {
        return blankToNull(Stream.of(parts)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(" ")));
    }

    private boolean isLatin1(String value) {
        return value.chars().allMatch(c -> c <= 0xFF);
    }

    private String markerLabel(TestResultMarker marker) {
        if (marker == null) {
            return null;
        }
        return switch (marker) {
            case UPPER_LIMIT -> "High";
            case LOWER_LIMIT -> "Low";
            case ABNORMAL_MARKER -> "Abnormal";
            case CRITICAL_UPPER -> "Critical High";
            case CRITICAL_LOWER -> "Critical Low";
            case NORMAL_MARKER -> "Normal";
            case UNKNOWN -> null;
        };
    }

    private String severityLabel(Severity severity) {
        if (severity == null) {
            return null;
        }
        return switch (severity) {
            case MILD_MINOR -> "Mild / Minor";
            case MODERATE -> "Moderate";
            case SEVERE -> "Severe";
            case CRITICAL -> "Critical";
            case NOTHING -> "None";
        };
    }

    private String humanize(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(value.name().toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String toPlainText(String value) {
        if (!isNotBlank(value)) {
            return null;
        }

        String text = value;
        if (htmlMarkup.matcher(text).find()) {
            text = text
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(p|div|h[1-6]|tr|ul|ol)>", "\n")
                    .replaceAll("(?i)<li[^>]*>", "\n- ")
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&");
        }

        text = text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() > maxReportTextLength) {
            text = text.substring(0, maxReportTextLength).trim() + " ...";
        }
        return blankToNull(text);
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String collectLabInvestigationResultValue(List<DiagnosticOrderTest> tests, Long claimCopyId) {
        List<String> values = findUsableLabResults(tests, claimCopyId).stream()
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

    private String formatLabResultValue(LabResultSource source) {
        if (source == null) {
            return null;
        }

        String number = cleanNumber(source.resultValueNumber());
        String text = clean(source.resultValueText());

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

    private String livePatientProblems(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientProblemRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 100))
                .stream()
                .filter(problem -> problem.getStatus() != null)
                .filter(problem -> ACTIVE.equalsIgnoreCase(problem.getStatus().name()))
                .map(PatientProblem::getCondition)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String liveSurgicalHistory(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return surgicalHistoryRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 100))
                .stream()
                .map(SurgicalHistory::getSurgery)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String liveSocialHistory(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return socialHistoryRepository
                .findAllByPatientId(patientId, PageRequest.of(0, 1))
                .stream()
                .map(this::mapSocialHistory)
                .filter(this::isNotBlank)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String copyPatientProblems(Long claimCopyId) {
        return claimEncounterCopyPatientProblemRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .map(ClaimEncounterCopyPatientProblem::getCondition)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String copySurgicalHistory(Long claimCopyId) {
        return claimEncounterCopySurgicalHistoryRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .map(ClaimEncounterCopySurgicalHistory::getSurgery)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String copySocialHistory(Long claimCopyId) {
        return claimEncounterCopySocialHistoryRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .sorted(Comparator.comparing(
                        ClaimEncounterCopySocialHistory::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(this::mapCopySocialHistory)
                .filter(this::isNotBlank)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String copyFamilyHistory(Long claimCopyId) {
        return joinHistory(claimEncounterCopyFamilyHistoryRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .map(this::mapCopyFamilyHistory));
    }

    private String copyHospitalizations(Long claimCopyId) {
        return joinHistory(claimEncounterCopyHospitalizationRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .map(this::mapCopyHospitalization));
    }

    private String copyCurrentMedications(Long claimCopyId) {
        return joinHistory(claimEncounterCopyCurrentMedicationRepository
                .findByClaimEncounterCopyIdAndStatusNot(claimCopyId, PatientHistoryStatus.CANCELLED)
                .stream()
                .map(this::mapCopyMedication));
    }

    private String joinHistory(Stream<String> values) {
        return blankToNull(values
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(", ")));
    }

    private String mapCopySocialHistory(ClaimEncounterCopySocialHistory source) {
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
        if (isNotBlank(source.getFreeText())) {
            values.add(source.getFreeText().trim());
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String mapCopyFamilyHistory(ClaimEncounterCopyFamilyHistory source) {
        if (source == null) {
            return null;
        }
        String condition = firstNonBlank(source.getCondition(), source.getFreeText());
        if (condition == null) {
            return null;
        }
        if (source.getRelation() == null) {
            return condition;
        }
        return condition + " (" + humanize(source.getRelation()) + ")";
    }

    private String mapCopyHospitalization(ClaimEncounterCopyHospitalization source) {
        if (source == null) {
            return null;
        }
        String reason = firstNonBlank(source.getReason(), source.getFreeText());
        String facility = clean(source.getFacility());
        if (reason != null && facility != null) {
            return reason + " at " + facility;
        }
        return firstNonBlank(reason, facility);
    }

    private String mapCopyMedication(ClaimEncounterCopyCurrentMedication source) {
        if (source == null) {
            return null;
        }
        if (isNotBlank(source.getFreeText())) {
            return source.getFreeText().trim();
        }

        List<String> parts = new ArrayList<>();
        String dosage = cleanNumber(source.getDosage());
        if (isNotBlank(dosage)) {
            parts.add(dosage);
        }
        if (source.getUnit() != null) {
            parts.add(humanize(source.getUnit()));
        }
        if (source.getFrequency() != null) {
            parts.add(humanize(source.getFrequency()));
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private LabResultSource toLabResultSource(DiagnosticOrderTestResult source) {
        return new LabResultSource(
                source.getId(),
                source.getOrderTestId(),
                source.getProfileTestId(),
                source.getResultValueNumber(),
                source.getResultValueText(),
                source.getMarker(),
                source.getNormalRangeValue(),
                source.getResultTypeAtEntry(),
                source.getApprovedDate(),
                source.getReviewDate(),
                source.getCreatedDate()
        );
    }

    private LabResultSource toLabResultSource(ClaimEncounterCopyDiagnosticOrderTestResult source) {
        return new LabResultSource(
                source.getId(),
                source.getOrderTestId(),
                source.getProfileTestId(),
                source.getResultValueNumber(),
                source.getResultValueText(),
                source.getMarker(),
                source.getNormalRangeValue(),
                source.getResultTypeAtEntry(),
                null,
                null,
                source.getCreatedDate()
        );
    }

    private RadiologyReportSource toRadiologyReportSource(DiagnosticOrderTestReport source) {
        return new RadiologyReportSource(
                source.getId(),
                source.getOrderTestId(),
                source.getProcessingStatus(),
                source.getSeverity(),
                source.getApprovedDate(),
                source.getSecondApprovedDate(),
                source.getReviewDate(),
                source.getCreatedDate(),
                source.getReport(),
                source.getCriticalFindings(),
                source.getRadiologistComments(),
                source.getRadiologistInformation(),
                source.getReviewBy(),
                source.getApprovedBy(),
                source.getSecondApprovedBy()
        );
    }

    private RadiologyReportSource toRadiologyReportSource(ClaimEncounterCopyDiagnosticOrderTestReport source) {
        return new RadiologyReportSource(
                source.getId(),
                source.getOrderTestId(),
                null,
                source.getSeverity(),
                null,
                null,
                null,
                source.getCreatedDate(),
                source.getReport(),
                source.getCriticalFindings(),
                source.getRadiologistComments(),
                source.getRadiologistInformation(),
                null,
                null,
                null
        );
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

    private String joinNonBlank(String separator, String... values) {
        if (values == null) {
            return null;
        }
        String joined = Stream.of(values)
                .filter(this::isNotBlank)
                .map(String::trim)
                .collect(Collectors.joining(separator));
        return blankToNull(joined);
    }

    private String joinDistinctNonBlank(String separator, String... values) {
        if (values == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!isNotBlank(value)) {
                continue;
            }
            String trimmed = value.trim();
            boolean alreadyPresent = parts.stream().anyMatch(trimmed::equalsIgnoreCase);
            if (!alreadyPresent) {
                parts.add(trimmed);
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
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

    private record LabResultSource(
            Long id,
            Long orderTestId,
            Long profileTestId,
            BigDecimal resultValueNumber,
            String resultValueText,
            TestResultMarker marker,
            String normalRangeValue,
            TestResultType resultTypeAtEntry,
            Instant approvedDate,
            Instant reviewDate,
            Instant createdDate
    ) {}

    private record RadiologyReportSource(
            Long id,
            Long orderTestId,
            DiagnosticStatus processingStatus,
            Severity severity,
            Instant approvedDate,
            Instant secondApprovedDate,
            Instant reviewDate,
            Instant createdDate,
            String report,
            String criticalFindings,
            String radiologistComments,
            String radiologistInformation,
            String reviewBy,
            String approvedBy,
            String secondApprovedBy
    ) {}
}