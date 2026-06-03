package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
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

    public List<WaseelApprovalSupportingInfo> toSupportingInfo(PatientEncounter encounter) {
        if (encounter == null || encounter.getId() == null) {
            return List.of();
        }

        List<WaseelApprovalSupportingInfo> result = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);

        addEncounterInfo(result, sequence, encounter);
        addChiefComplaint(result, sequence, encounter.getId());
        addVitalSigns(result, sequence, encounter.getId());
        addBodyMeasurements(result, sequence, encounter.getId());

        return result;
    }

    private void addEncounterInfo(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            PatientEncounter encounter
    ) {
        if (isNotBlank(encounter.getNotes())) {
            result.add(info(sequence, "patient-history", encounter.getNotes(), "", ""));
        }

        if (isNotBlank(encounter.getPhysicalExaminationSummery())) {
            result.add(info(sequence, "physical-examination", encounter.getPhysicalExaminationSummery(), "", ""));
        }

        if (isNotBlank(encounter.getChiefComplaint())) {
            result.add(info(sequence, "chief-complaint", encounter.getChiefComplaint(), "", ""));
        }
    }

    private void addChiefComplaint(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        chiefComplainRepository.findTopByEncounter_IdOrderByIdDesc(encounterId)
                .ifPresent(chief -> {
                    result.add(info(sequence, "chief-complaint", chief.getChiefComplaint(), "", ""));

                    if (isNotBlank(chief.getCaseUnderstanding())) {
                        result.add(info(sequence, "history-of-present-illness", chief.getCaseUnderstanding(), "", ""));
                    }

                    if (isNotBlank(chief.getSeverity())) {
                        result.add(info(sequence, "pain-score", "", chief.getSeverity(), ""));
                    }
                });
    }

    private void addVitalSigns(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        vitalSignsRepository.findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounterId)
                .ifPresent(vital -> {
                    result.add(info(sequence, "vital-sign-systolic", "", String.valueOf(vital.getBloodPressureSystolic()), "mmHg"));
                    result.add(info(sequence, "vital-sign-diastolic", "", String.valueOf(vital.getBloodPressureDiastolic()), "mmHg"));
                    result.add(info(sequence, "pulse", "", String.valueOf(vital.getHeartRate()), "beats/min"));
                    result.add(info(sequence, "temperature", "", vital.getTemperature().toPlainString(), "Cel"));
                    result.add(info(sequence, "oxygen-saturation", "", vital.getOxygenSaturation().toPlainString(), "%"));
                    result.add(info(sequence, "respiratory-rate", "", String.valueOf(vital.getRespiratoryRate()), "breaths/min"));
                });
    }

    private void addBodyMeasurements(
            List<WaseelApprovalSupportingInfo> result,
            AtomicInteger sequence,
            Long encounterId
    ) {
        bodyMeasurementsRepository.findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(encounterId)
                .ifPresent(body -> {
                    result.add(info(sequence, "vital-sign-weight", "", body.getWeight().toPlainString(), "kg"));
                    result.add(info(sequence, "vital-sign-height", "", body.getHeight().toPlainString(), "cm"));
                });
    }

    private WaseelApprovalSupportingInfo info(
            AtomicInteger sequence,
            String category,
            String value,
            String reason,
            String unit
    ) {
        return new WaseelApprovalSupportingInfo(
                sequence.getAndIncrement(),
                category,
                "",
                "",
                "",
                safe(value),
                safe(reason),
                "",
                "",
                "",
                safe(unit),
                ""
        );
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}