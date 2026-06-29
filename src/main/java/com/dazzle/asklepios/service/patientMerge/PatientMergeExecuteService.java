package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.domain.enumeration.MergedStatus;
import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeDecisionDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteDTO;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeRecordTransferHelper;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeSupportHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeExecuteVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PatientMergeExecuteService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeExecuteService.class);

    private static final String TX_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final PatientRepository patientRepository;
    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeAnalysisService analysisService;
    private final PatientMergeSupportHelper supportService;
    private final PatientMergeRecordTransferHelper recordTransferService;

    public PatientMergeExecuteService(
            PatientRepository patientRepository,
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeAnalysisService analysisService,
            PatientMergeSupportHelper supportService, PatientMergeRecordTransferHelper recordTransferService
    ) {
        this.patientRepository = patientRepository;
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.tableConfigRepository = tableConfigRepository;
        this.analysisService = analysisService;
        this.supportService = supportService;
        this.recordTransferService = recordTransferService;
    }

    public PatientMergeExecuteVM executeMerge(
            PatientMergeExecuteDTO request
    ) {
        LOG.debug(
                "Executing patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.fromPatientId() : null,
                request != null ? request.toPatientId() : null
        );

        validateRequest(request);

        Patient fromPatient = findPatientOrThrow(
                request.fromPatientId(),
                "From patient not found"
        );

        Patient toPatient = findPatientOrThrow(
                request.toPatientId(),
                "To patient not found"
        );

        PatientMergePreviewVM analysis = analysisService.analyze(
                request.fromPatientId(),
                request.toPatientId()
        );

        PatientMergeLog mergeLog = createMergeLog(
                fromPatient,
                toPatient,
                request.reason()
        );

        applyUserDecisions(
                request.decisions(),
                toPatient.getId(),
                mergeLog.getId(),
                mergeLog
        );

        applyAutoTransfers(
                analysis,
                toPatient.getId(),
                mergeLog.getId(),
                mergeLog
        );

        transferEmrRecords(
                fromPatient.getId(),
                toPatient.getId(),
                mergeLog.getId()
        );

        markSourcePatientAsMerged(fromPatient, toPatient);

        LOG.info(
                "Patient merge completed successfully. mergeLogId={}, transactionNumber={}, fromPatientId={}, toPatientId={}",
                mergeLog.getId(),
                mergeLog.getTransactionNumber(),
                fromPatient.getId(),
                toPatient.getId()
        );

        return new PatientMergeExecuteVM(
                mergeLog.getId(),
                fromPatient.getId(),
                toPatient.getId(),
                "MERGED",
                mergeLog.getTransactionNumber()
        );
    }

    private PatientMergeLog createMergeLog(
            Patient fromPatient,
            Patient toPatient,
            String reason
    ) {
        Instant now = Instant.now();
        String username = currentUsername();

        PatientMergeLog mergeLog = PatientMergeLog.builder()
                .transactionNumber(generateTransactionNumber())
                .fromPatient(fromPatient)
                .toPatient(toPatient)
                .mergeStatus(MergedStatus.MERGED)
                .mergedBy(username)
                .mergedAt(now)
                .reason(reason)
                .build();

        PatientMergeLog saved =
                patientMergeLogRepository.save(mergeLog);

        LOG.debug(
                "Created merge log. mergeLogId={}, transactionNumber={}, fromPatientId={}, toPatientId={}, mergedBy={}",
                saved.getId(),
                saved.getTransactionNumber(),
                fromPatient.getId(),
                toPatient.getId(),
                username
        );

        return saved;
    }


    private String generateTransactionNumber() {

        String value;

        do {
            value = "PM-" + randomPart(6);
        } while (
                patientMergeLogRepository.existsByTransactionNumber(value)
        );

        return value;
    }

    private String randomPart(int length) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(
                    TX_CHARS.charAt(
                            RANDOM.nextInt(TX_CHARS.length())
                    )
            );
        }

        return sb.toString();
    }

    private void validateRequest(
            PatientMergeExecuteDTO request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Request is required",
                    "PatientMerge",
                    "request.required"
            );
        }

        if (request.fromPatientId() == null
                || request.toPatientId() == null) {

            throw new BadRequestAlertException(
                    "Patient IDs are required",
                    "PatientMerge",
                    "ids.required"
            );
        }

        if (request.fromPatientId().equals(request.toPatientId())) {
            throw new BadRequestAlertException(
                    "Cannot merge same patient",
                    "PatientMerge",
                    "same.patient"
            );
        }
    }

    private Patient findPatientOrThrow(
            Long patientId,
            String message
    ) {
        return patientRepository.findById(patientId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                message,
                                "Patient",
                                patientId.toString()
                        )
                );
    }

    private void applyUserDecisions(
            List<PatientMergeDecisionDTO> decisions,
            Long toPatientId,
            Long mergeLogId,
            PatientMergeLog mergeLog
    ) {
        if (decisions == null || decisions.isEmpty()) {
            return;
        }

        decisions.forEach(decision -> {
            saveMasterDecision(mergeLog, decision);
            applyDecision(decision, toPatientId, mergeLogId);
        });
    }

    private void applyDecision(
            PatientMergeDecisionDTO decision,
            Long toPatientId,
            Long mergeLogId
    ) {
        if (decision.finalDecision() == null) {
            return;
        }

        switch (decision.finalDecision()) {

            case KEEP_TO -> {
                return;
            }


            case TAKE_FROM, MANUAL -> updateFieldValue(
                    decision.tableName(),
                    decision.fieldName(),
                    decision.toRecordId(),
                    resolveSelectedValue(decision)
            );

            case ADD_FROM_RECORD -> moveRecordToTarget(
                    decision.tableName(),
                    decision.fromRecordId(),
                    toPatientId,
                    mergeLogId
            );

            default -> throw new BadRequestAlertException(
                    "Unsupported merge decision",
                    "PatientMerge",
                    "unsupported.decision"
            );
        }
    }

    private void applyAutoTransfers(
            PatientMergePreviewVM analysis,
            Long toPatientId,
            Long mergeLogId,
            PatientMergeLog mergeLog
    ) {
        if (analysis == null
                || analysis.autoTransfers() == null
                || analysis.autoTransfers().isEmpty()) {

            return;
        }

        analysis.autoTransfers().forEach(autoTransfer -> {
            applyAutoTransfer(autoTransfer, toPatientId, mergeLogId);
            saveAutoTransferAudit(mergeLog, autoTransfer);
        });
    }


    private void applyAutoTransfer(
            PatientMergeAutoTransferDTO autoTransfer,
            Long toPatientId,
            Long mergeLogId
    ) {
        if (autoTransfer.suggestedDecision()
                == MergeDecision.ADD_FROM_RECORD) {

            moveRecordToTarget(
                    autoTransfer.tableName(),
                    autoTransfer.fromRecordId(),
                    toPatientId,
                    mergeLogId
            );

            return;
        }

        if (autoTransfer.suggestedDecision()
                == MergeDecision.TAKE_FROM) {

            updateFieldValue(
                    autoTransfer.tableName(),
                    autoTransfer.fieldName(),
                    autoTransfer.toRecordId(),
                    autoTransfer.selectedValue()
            );
        }
    }

    private void updateFieldValue(
            String tableName,
            String fieldName,
            Long recordId,
            String selectedValue
    ) {
        recordTransferService.updateFieldValue(
                tableName,
                fieldName,
                recordId,
                selectedValue
        );
    }

    private String resolveSelectedValue(
            PatientMergeDecisionDTO decision
    ) {
        if (decision.finalDecision() == MergeDecision.TAKE_FROM) {
            return decision.fromValue();
        }

        return decision.selectedValue();
    }

    private void moveRecordToTarget(
            String tableName,
            Long fromRecordId,
            Long toPatientId,
            Long mergeLogId
    ) {
        if (tableName == null || fromRecordId == null) {
            return;
        }

        PatientMergeTableConfig config =
                supportService.findTableConfig(tableName);

        if (recordTransferService.shouldSkipTransfer(config, fromRecordId, toPatientId)) {
            LOG.debug(
                    "Skipping duplicate record transfer. tableName={}, recordId={}",
                    config.getTableName(),
                    fromRecordId
            );
            return;
        }

        Long oldPatientId =
                recordTransferService.getRecordPatientId(config, fromRecordId);
        recordTransferService.moveRecordToTarget(
                config,
                fromRecordId,
                toPatientId
        );
        recordTransferService.saveMovedItemLog(
                mergeLogId,
                config,
                fromRecordId,
                oldPatientId,
                toPatientId
        );


    }


    private void transferEmrRecords(
            Long fromPatientId,
            Long toPatientId,
            Long mergeLogId
    ) {
        List<PatientMergeTableConfig> configs =
                tableConfigRepository
                        .findByEnabledTrue();

        configs.stream()
                .filter(config ->
                        config.getMergeCategory()
                                == PatientMergeCategory.EMR
                )
                .forEach(config ->
                        transferEmrTable(
                                config,
                                fromPatientId,
                                toPatientId,
                                mergeLogId
                        )
                );
    }

    private void transferEmrTable(
            PatientMergeTableConfig config,
            Long fromPatientId,
            Long toPatientId,
            Long mergeLogId
    ) {
        List<Map<String, Object>> fromRecords =
                recordTransferService.getRecordIdsByPatientId(
                        config,
                        fromPatientId
                );

        for (Map<String, Object> fromRecord : fromRecords) {
            Long recordId =
                    supportService.toLong(
                            fromRecord.get(config.getPrimaryKeyColumnName())
                    );

            if (recordId == null) {
                continue;
            }

            moveRecordToTarget(
                    config.getTableName(),
                    recordId,
                    toPatientId,
                    mergeLogId
            );
        }
    }

    private void markSourcePatientAsMerged(
            Patient fromPatient,
            Patient toPatient
    ) {
        fromPatient.setPatientStatus(PatientStatus.MERGED);
        fromPatient.setMergedIntoPatientId(toPatient.getId());
        fromPatient.setMergedAt(Instant.now());
        fromPatient.setMergedBy(currentUsername());

        patientRepository.save(fromPatient);
    }

    private void saveMasterDecision(
            PatientMergeLog mergeLog,
            PatientMergeDecisionDTO dto
    ) {
        patientMergeMasterDecisionRepository.save(
                PatientMergeMasterDecision.builder()
                        .mergeLog(mergeLog)
                        .entityName(dto.entityName())
                        .tableName(dto.tableName())
                        .fromRecordId(dto.fromRecordId())
                        .toRecordId(dto.toRecordId())
                        .matchKey(dto.matchKey())
                        .fieldName(dto.fieldName())
                        .fieldLabel(dto.fieldLabel())
                        .fromValue(dto.fromValue())
                        .toValue(dto.toValue())
                        .finalDecision(dto.finalDecision())
                        .selectedValue(dto.selectedValue())
                        .build()
        );
    }

    private void saveAutoTransferAudit(
            PatientMergeLog mergeLog,
            PatientMergeAutoTransferDTO autoTransfer
    ) {
        patientMergeMasterDecisionRepository.save(
                PatientMergeMasterDecision.builder()
                        .mergeLog(mergeLog)
                        .entityName(autoTransfer.entityName())
                        .tableName(autoTransfer.tableName())
                        .fromRecordId(autoTransfer.fromRecordId())
                        .toRecordId(autoTransfer.toRecordId())
                        .matchKey(autoTransfer.matchKey())
                        .fieldName(autoTransfer.fieldName())
                        .fieldLabel(autoTransfer.fieldLabel())
                        .fromValue(autoTransfer.fromValue())
                        .toValue(autoTransfer.toValue())
                        .finalDecision(autoTransfer.suggestedDecision())
                        .selectedValue(autoTransfer.selectedValue())
                        .build()
        );
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "unauthenticated",
                                "diagnostic_order_tests_result",
                                "No authenticated user"
                        )
                );
    }



}