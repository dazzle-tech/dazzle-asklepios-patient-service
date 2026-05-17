package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeFieldConfig;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionChangesVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientMergeTransactionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeTransactionService.class);

    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;
    private final PatientMergeSupportService supportService;

    public PatientMergeTransactionService(
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientMergeSupportService supportService
    ) {
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.supportService = supportService;
    }

    public List<PatientMergeTransactionVM> getTransactions(
            Long patientId
    ) {
        LOG.debug(
                "Loading patient merge transactions. patientId={}",
                patientId
        );

        List<PatientMergeLog> logs = loadMergeLogs(patientId);

        LOG.debug(
                "Loaded {} merge transactions",
                logs.size()
        );

        return logs.stream()
                .map(this::toVm)
                .toList();
    }

    public PatientMergeTransactionChangesVM getChanges(
            Long mergeLogId
    ) {
        LOG.debug(
                "Loading merge transaction changes. mergeLogId={}",
                mergeLogId
        );

        List<PatientMergeMasterDecision> decisions =
                patientMergeMasterDecisionRepository.findByMergeLogId(
                        mergeLogId
                );

        List<PatientMergeTransactionChangesVM.FieldChangeVM> fieldChanges =
                decisions.stream()
                        .filter(this::isFieldChange)
                        .map(this::toFieldChangeVM)
                        .toList();

        LOG.debug(
                "Loaded {} transaction field changes. mergeLogId={}",
                fieldChanges.size(),
                mergeLogId
        );

        return PatientMergeTransactionChangesVM.builder()
                .mergeLogId(mergeLogId)
                .fieldChanges(fieldChanges)
                .build();
    }

    private List<PatientMergeLog> loadMergeLogs(
            Long patientId
    ) {
        if (patientId != null) {
            return patientMergeLogRepository
                    .findByFromPatientIdOrToPatientIdOrderByMergedAtDesc(
                            patientId,
                            patientId
                    );
        }

        return patientMergeLogRepository
                .findAllByOrderByMergedAtDesc();
    }

    private PatientMergeTransactionVM toVm(
            PatientMergeLog log
    ) {
        Patient fromPatient = log.getFromPatient();
        Patient toPatient = log.getToPatient();

        return PatientMergeTransactionVM.builder()
                .mergeLogId(log.getId())

                .fromPatientId(
                        fromPatient != null
                                ? fromPatient.getId()
                                : null
                )

                .fromPatientName(
                        supportService.buildPatientName(fromPatient)
                )

                .fromPatientMrn(
                        fromPatient != null
                                ? fromPatient.getMedicalRecordNumber()
                                : null
                )

                .toPatientId(
                        toPatient != null
                                ? toPatient.getId()
                                : null
                )

                .toPatientName(
                        supportService.buildPatientName(toPatient)
                )

                .toPatientMrn(
                        toPatient != null
                                ? toPatient.getMedicalRecordNumber()
                                : null
                )

                .mergeStatus(log.getMergeStatus())
                .mergedBy(log.getMergedBy())
                .mergedAt(log.getMergedAt())

                .undoneBy(log.getUndoneBy())
                .undoneAt(log.getUndoneAt())

                .reason(log.getReason())

                .canUndo(canUndo(log))
                .transactionNumber(log.getTransactionNumber())
                .build();
    }

    private Boolean canUndo(
            PatientMergeLog log
    ) {
        if (!"MERGED".equals(log.getMergeStatus())) {
            return false;
        }

        if (log.getToPatient() == null
                || log.getMergedAt() == null) {

            return false;
        }

        return !patientMergeLogRepository
                .existsByToPatientIdAndMergedAtAfterAndMergeStatus(
                        log.getToPatient().getId(),
                        log.getMergedAt(),
                        "MERGED"
                );
    }

    private boolean isFieldChange(
            PatientMergeMasterDecision decision
    ) {
        if (decision.getFieldName() == null
                || decision.getFieldName().isBlank()) {

            return false;
        }

        return decision.getFinalDecision() == MergeDecision.TAKE_FROM
                || decision.getFinalDecision() == MergeDecision.MANUAL;
    }

    private PatientMergeTransactionChangesVM.FieldChangeVM toFieldChangeVM(
            PatientMergeMasterDecision decision
    ) {
        PatientMergeFieldConfig fieldConfig =
                supportService.findFieldConfig(
                        decision.getTableName(),
                        decision.getFieldName()
                );

        return PatientMergeTransactionChangesVM.FieldChangeVM.builder()

                .entityName(decision.getEntityName())
                .tableName(decision.getTableName())

                .fromRecordId(decision.getFromRecordId())
                .toRecordId(decision.getToRecordId())

                .fieldName(decision.getFieldName())
                .fieldLabel(decision.getFieldLabel())

                .oldValue(decision.getToValue())
                .newValue(decision.getSelectedValue())

                .decision(
                        decision.getFinalDecision() != null
                                ? decision.getFinalDecision().name()
                                : null
                )

                .fieldType(
                        supportService.getColumnType(
                                decision.getTableName(),
                                decision.getFieldName()
                        )
                )

                .inputType(
                        fieldConfig != null
                                ? fieldConfig.getInputType()
                                : null
                )

                .inputSource(
                        fieldConfig != null
                                ? fieldConfig.getInputSource()
                                : null
                )

                .build();
    }
}