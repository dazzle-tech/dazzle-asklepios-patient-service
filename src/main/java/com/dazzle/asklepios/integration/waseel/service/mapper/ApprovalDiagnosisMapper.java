package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.ICDTreeClient;
import com.dazzle.asklepios.client.setup.dto.ICDDiagnosisDTO;
import com.dazzle.asklepios.domain.ClaimEncounterDiagnosis;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalDiagnosisMapper {

    private static final String PRINCIPAL = "principal";
    private static final String SECONDARY = "secondary";

    private final ICDTreeClient icdTreeClient;

    public List<WaseelApprovalDiagnosis> toWaseelDiagnosisList(List<PatientDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        return diagnoses.stream()
                .map(diagnosis -> toWaseelDiagnosis(diagnosis.getDiagnosisId(), diagnosis.getType(), sequence.getAndIncrement()))
                .toList();
    }

    public List<WaseelApprovalDiagnosis> toWaseelClaimDiagnosisList(List<ClaimEncounterDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        return diagnoses.stream()
                .map(diagnosis -> toWaseelDiagnosis(diagnosis.getDiagnosisId(), diagnosis.getType(), sequence.getAndIncrement()))
                .toList();
    }

    private WaseelApprovalDiagnosis toWaseelDiagnosis(Long diagnosisId, DiagnosisType type, Integer sequence) {
        ICDDiagnosisDTO icd = getIcdDiagnosis(diagnosisId);

        return new WaseelApprovalDiagnosis(
                sequence,
                firstNonBlank(icd.icdFullDescription(), icd.icdShortDescription()),
                mapDiagnosisType(type),
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

    /**
     * Claim payload sends every visit diagnosis. Waseel allows one principal and any
     * other diagnosis types. The visit primary stays principal and is placed first.
     * Extra principals become secondary. If the visit has no primary, the first
     * diagnosis becomes the principal so the claim still has one.
     */
    public static List<WaseelApprovalDiagnosis> withSinglePrincipal(List<WaseelApprovalDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            return List.of();
        }

        WaseelApprovalDiagnosis principal = null;
        int principalCount = 0;
        for (WaseelApprovalDiagnosis diagnosis : diagnoses) {
            if (isPrincipal(diagnosis)) {
                principalCount++;
                if (principal == null) {
                    principal = diagnosis;
                }
            }
        }
        if (principal == null) {
            principal = diagnoses.get(0);
            log.warn(
                    "Claim has no primary visit diagnosis. Sending {} as principal and keeping the other visit diagnoses.",
                    principal.diagnosisCode()
            );
        } else if (principalCount > 1) {
            log.warn(
                    "Claim visit has {} primary diagnoses. Keeping {} as the only principal.",
                    principalCount,
                    principal.diagnosisCode()
            );
        }

        List<WaseelApprovalDiagnosis> ordered = new ArrayList<>();
        ordered.add(principal);
        for (WaseelApprovalDiagnosis diagnosis : diagnoses) {
            if (diagnosis != principal) {
                ordered.add(diagnosis);
            }
        }

        List<WaseelApprovalDiagnosis> result = new ArrayList<>();
        int sequence = 1;
        for (WaseelApprovalDiagnosis diagnosis : ordered) {
            result.add(new WaseelApprovalDiagnosis(
                    sequence++,
                    diagnosis.diagnosisDescription(),
                    diagnosis == principal ? PRINCIPAL : SECONDARY,
                    diagnosis.diagnosisCode()
            ));
        }
        return result;
    }

    private static boolean isPrincipal(WaseelApprovalDiagnosis diagnosis) {
        return diagnosis != null
                && diagnosis.type() != null
                && PRINCIPAL.equals(diagnosis.type().trim().toLowerCase(Locale.ROOT));
    }

    private String mapDiagnosisType(DiagnosisType type) {
        return type == DiagnosisType.PRIMARY
                ? PRINCIPAL
                : SECONDARY;
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