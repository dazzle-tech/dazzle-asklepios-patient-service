package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalSupportingInfoMapper {

    private final ChiefComplainRepository chiefComplainRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final JdbcTemplate jdbcTemplate;

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
        String reasonOfVisit = latestValue(
                """
                select reason_of_visit
                from patient_observations_complaints
                where encounter_id = ?
                  and is_active = true
                  and reason_of_visit is not null
                  and trim(reason_of_visit) <> ''
                order by id desc
                limit 1
                """,
                encounterId
        );

        String progressNote = latestValue(
                """
                select note_text
                from progress_notes
                where encounter_id = ?
                  and note_text is not null
                  and trim(note_text) <> ''
                  and cancelled_date is null
                order by id desc
                limit 1
                """,
                encounterId
        );

        String assessment = latestValue(
                """
                select assessment
                from encounter_assessments
                where encounter_id = ?
                  and assessment is not null
                  and trim(assessment) <> ''
                order by id desc
                limit 1
                """,
                encounterId
        );

        String patientProblems = latestValue(
                """
                select string_agg(condition, ', ')
                from patient_problems
                where patient_id = ?
                  and status = 'ACTIVE'
                  and condition is not null
                  and trim(condition) <> ''
                """,
                patientId
        );

        String surgicalHistory = latestValue(
                """
                select string_agg(surgery, ', ')
                from surgical_history
                where patient_id = ?
                  and status = 'ACTIVE'
                  and surgery is not null
                  and trim(surgery) <> ''
                """,
                patientId
        );

        String socialHistory = latestValue(
                """
                select concat_ws(', ',
                    case when is_current_smoker = true then 'Current smoker' end,
                    case when is_previous_smoker = true then 'Previous smoker' end,
                    case when alcohol_consumption = true then 'Alcohol consumption' end,
                    case when substance_use = true then 'Substance use' end,
                    nullif(physical_limitation, ''),
                    nullif(diagnosed_eating_disorders, '')
                )
                from social_history
                where patient_id = ?
                  and status = 'ACTIVE'
                order by id desc
                limit 1
                """,
                patientId
        );

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

    private String latestValue(String sql, Object... args) {
        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            if (arg == null) {
                return null;
            }
        }

        List<String> values = jdbcTemplate.queryForList(sql, String.class, args);

        return values.stream()
                .filter(this::isNotBlank)
                .map(String::trim)
                .findFirst()
                .orElse(null);
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

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}