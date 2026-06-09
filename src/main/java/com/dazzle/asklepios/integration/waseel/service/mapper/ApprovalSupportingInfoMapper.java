package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApprovalSupportingInfoMapper {

    private static final String ACTIVE = "ACTIVE";

    private final ChiefComplainRepository chiefComplainRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final ProgressNoteRepository progressNoteRepository;
    private final EncounterAssessmentRepository encounterAssessmentRepository;
    private final PatientProblemRepository patientProblemRepository;
    private final SurgicalHistoryRepository surgicalHistoryRepository;
    private final SocialHistoryRepository socialHistoryRepository;

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

        addRequiredClinicalText(result, sequence, encounter, chief, encounterId, patientId);
        addVitalSigns(result, sequence, encounterId);
        addBodyMeasurements(result, sequence, encounterId);

        return result;
    }

    private void addRequiredClinicalText(
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

        String chiefComplaint = required(
                firstNonBlank(
                        encounter.getChiefComplaint(),
                        chief == null ? null : chief.getChiefComplaint(),
                        reasonOfVisit
                ),
                "Chief Complaint is required before Waseel pre-authorization"
        );

        String historyOfPresentIllness = required(
                firstNonBlank(
                        chief == null ? null : chief.getCaseUnderstanding(),
                        progressNote,
                        reasonOfVisit,
                        chiefComplaint
                ),
                "History of Present Illness is required before Waseel pre-authorization"
        );

        String patientHistory = required(
                firstNonBlank(
                        encounter.getNotes(),
                        patientProblems,
                        socialHistory,
                        surgicalHistory
                ),
                "Patient History is required before Waseel pre-authorization"
        );

        String physicalExamination = required(
                firstNonBlank(
                        encounter.getPhysicalExaminationSummery(),
                        assessment
                ),
                "Physical Examination is required before Waseel pre-authorization"
        );

        String investigationResult = required(
                firstNonBlank(
                        progressNote,
                        assessment
                ),
                "Investigation Result is required before Waseel pre-authorization"
        );

        String treatmentPlan = required(
                firstNonBlank(
                        assessment,
                        progressNote
                ),
                "Treatment Plan is required before Waseel pre-authorization"
        );

        result.add(textInfo(sequence, "chief-complaint", chiefComplaint));
        result.add(textInfo(sequence, "history-of-present-illness", historyOfPresentIllness));
        result.add(textInfo(sequence, "patient-history", patientHistory));
        result.add(textInfo(sequence, "physical-examination", physicalExamination));
        result.add(textInfo(sequence, "investigation-result", investigationResult));
        result.add(textInfo(sequence, "treatment-plan", treatmentPlan));
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

    private void addVitalSigns(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        VitalSigns vital = vitalSignsRepository
                .findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Vital signs are required before Waseel pre-authorization",
                        "preAuthorization",
                        "waseel.vitalSigns.required"
                ));

        if (vital.getBloodPressureSystolic() == null) {
            throw requiredField("Blood Pressure Systolic is required before Waseel pre-authorization");
        }

        if (vital.getBloodPressureDiastolic() == null) {
            throw requiredField("Blood Pressure Diastolic is required before Waseel pre-authorization");
        }

        if (vital.getHeartRate() == null) {
            throw requiredField("Heart Rate is required before Waseel pre-authorization");
        }

        if (vital.getTemperature() == null) {
            throw requiredField("Temperature is required before Waseel pre-authorization");
        }

        if (vital.getOxygenSaturation() == null) {
            throw requiredField("Oxygen Saturation is required before Waseel pre-authorization");
        }

        if (vital.getRespiratoryRate() == null) {
            throw requiredField("Respiratory Rate is required before Waseel pre-authorization");
        }

        result.add(valueInfo(sequence, "vital-sign-systolic", String.valueOf(vital.getBloodPressureSystolic()), "mmHg"));
        result.add(valueInfo(sequence, "vital-sign-diastolic", String.valueOf(vital.getBloodPressureDiastolic()), "mmHg"));
        result.add(valueInfo(sequence, "pulse", String.valueOf(vital.getHeartRate()), "beats/min"));
        result.add(valueInfo(sequence, "temperature", vital.getTemperature().stripTrailingZeros().toPlainString(), "Cel"));
        result.add(valueInfo(sequence, "oxygen-saturation", vital.getOxygenSaturation().stripTrailingZeros().toPlainString(), "%"));
        result.add(valueInfo(sequence, "respiratory-rate", String.valueOf(vital.getRespiratoryRate()), "breaths/min"));
    }

    private void addBodyMeasurements(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        BodyMeasurements body = bodyMeasurementsRepository
                .findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Body measurements are required before Waseel pre-authorization",
                        "preAuthorization",
                        "waseel.bodyMeasurements.required"
                ));

        if (body.getWeight() == null) {
            throw requiredField("Weight is required before Waseel pre-authorization");
        }

        if (body.getHeight() == null) {
            throw requiredField("Height is required before Waseel pre-authorization");
        }

        result.add(valueInfo(sequence, "vital-sign-weight", body.getWeight().stripTrailingZeros().toPlainString(), "kg"));
        result.add(valueInfo(sequence, "vital-sign-height", body.getHeight().stripTrailingZeros().toPlainString(), "cm"));
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

    private WaseelApprovalSupportingInfo valueInfo(
            AtomicInteger sequence,
            String category,
            String value,
            String unit
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
                clean(unit),
                null
        );
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw requiredField(message);
        }

        return value.trim();
    }

    private BadRequestAlertException requiredField(String message) {
        return new BadRequestAlertException(
                message,
                "preAuthorization",
                "waseel.supportingInfo.required"
        );
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