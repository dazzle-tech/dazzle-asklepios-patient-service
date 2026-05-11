package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionChangesVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionVM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientMergeTransactionService {

    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;

    public PatientMergeTransactionService(
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository
    ) {
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
    }

    public List<PatientMergeTransactionVM> getTransactions(Long patientId) {
        List<PatientMergeLog> logs;

        if (patientId != null) {
            logs = patientMergeLogRepository.findByFromPatientIdOrToPatientIdOrderByMergedAtDesc(
                    patientId,
                    patientId
            );
        } else {
            logs = patientMergeLogRepository.findAllByOrderByMergedAtDesc();
        }

        return logs.stream()
                .map(this::toVm)
                .toList();
    }

    public PatientMergeTransactionChangesVM getChanges(Long mergeLogId) {
        List<PatientMergeMasterDecision> decisions =
                patientMergeMasterDecisionRepository.findByMergeLogId(mergeLogId);

        List<PatientMergeTransactionChangesVM.FieldChangeVM> fieldChanges =
                decisions.stream()
                        .filter(this::isFieldChange)
                        .map(this::toFieldChangeVM)
                        .toList();

        return PatientMergeTransactionChangesVM.builder()
                .mergeLogId(mergeLogId)
                .fieldChanges(fieldChanges)
                .build();
    }

    private PatientMergeTransactionVM toVm(PatientMergeLog log) {
        Patient fromPatient = log.getFromPatient();
        Patient toPatient = log.getToPatient();

        return PatientMergeTransactionVM.builder()
                .mergeLogId(log.getId())
                .fromPatientId(fromPatient != null ? fromPatient.getId() : null)
                .fromPatientName(buildPatientName(fromPatient))
                .toPatientId(toPatient != null ? toPatient.getId() : null)
                .toPatientName(buildPatientName(toPatient))
                .mergeStatus(log.getMergeStatus())
                .mergedBy(log.getMergedBy())
                .mergedAt(log.getMergedAt())
                .undoneBy(log.getUndoneBy())
                .undoneAt(log.getUndoneAt())
                .reason(log.getReason())
                .canUndo(canUndo(log))
                .build();
    }

    private Boolean canUndo(PatientMergeLog log) {
        if (!"MERGED".equals(log.getMergeStatus())) {
            return false;
        }

        if (log.getToPatient() == null || log.getMergedAt() == null) {
            return false;
        }

        return !patientMergeLogRepository.existsByToPatientIdAndMergedAtAfterAndMergeStatus(
                log.getToPatient().getId(),
                log.getMergedAt(),
                "MERGED"
        );
    }

    private boolean isFieldChange(PatientMergeMasterDecision decision) {
        if (decision.getFieldName() == null || decision.getFieldName().isBlank()) {
            return false;
        }

        return decision.getFinalDecision() == MergeDecision.TAKE_FROM
                || decision.getFinalDecision() == MergeDecision.MANUAL;
    }

    private PatientMergeTransactionChangesVM.FieldChangeVM toFieldChangeVM(
            PatientMergeMasterDecision decision
    ) {
        return PatientMergeTransactionChangesVM.FieldChangeVM.builder()
                .entityName(decision.getEntityName())
                .tableName(decision.getTableName())
                .fromRecordId(decision.getFromRecordId())
                .toRecordId(decision.getToRecordId())
                .fieldName(decision.getFieldName())
                .fieldLabel(decision.getFieldLabel())
                .oldValue(decision.getToValue())
                .newValue(decision.getSelectedValue())
                .decision(decision.getFinalDecision() != null ? decision.getFinalDecision().name() : null)
                .build();
    }

    private String buildPatientName(Patient patient) {
        if (patient == null) {
            return "";
        }

        return String.join(
                        " ",
                        safe(patient.getFirstName()),
                        safe(patient.getSecondName()),
                        safe(patient.getThirdName()),
                        safe(patient.getLastName())
                )
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}