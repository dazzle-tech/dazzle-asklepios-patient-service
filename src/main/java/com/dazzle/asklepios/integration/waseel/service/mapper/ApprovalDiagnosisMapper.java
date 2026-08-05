package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.ICDTreeClient;
import com.dazzle.asklepios.client.setup.dto.ICDDiagnosisDTO;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalDiagnosisMapper {

    private final ICDTreeClient icdTreeClient;

    public List<WaseelApprovalDiagnosis> toWaseelDiagnosisList(List<PatientDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        return diagnoses.stream()
                .map(diagnosis -> toWaseelDiagnosis(diagnosis, sequence.getAndIncrement()))
                .toList();
    }

    private WaseelApprovalDiagnosis toWaseelDiagnosis(PatientDiagnosis patientDiagnosis, Integer sequence) {
        ICDDiagnosisDTO icd = getIcdDiagnosis(patientDiagnosis.getDiagnosisId());

        return new WaseelApprovalDiagnosis(
                sequence,
                firstNonBlank(icd.icdFullDescription(), icd.icdShortDescription()),
                mapDiagnosisType(patientDiagnosis.getType()),
                safe(icd.icdCode())
        );
    }

    private ICDDiagnosisDTO getIcdDiagnosis(Long diagnosisId) {
        try {
            return icdTreeClient.getDiagnosisById(diagnosisId);
        } catch (FeignException.NotFound ex) {
            return new ICDDiagnosisDTO(
                    diagnosisId,
                    null,
                    "",
                    "",
                    "",
                    "Diagnosis not found",
                    "Diagnosis not found"
            );
        }
    }

    private String mapDiagnosisType(DiagnosisType type) {
        return type == DiagnosisType.PRIMARY
                ? "principal"
                : "secondary";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}