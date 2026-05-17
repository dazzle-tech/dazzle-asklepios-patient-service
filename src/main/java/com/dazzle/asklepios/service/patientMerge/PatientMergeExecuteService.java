package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
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
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeExecuteVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
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
    private final PatientMergeSupportService supportService;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeExecuteService(
            PatientRepository patientRepository,
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeAnalysisService analysisService,
            PatientMergeSupportService supportService,
            JdbcTemplate jdbcTemplate
    ) {
        this.patientRepository = patientRepository;
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.tableConfigRepository = tableConfigRepository;
        this.analysisService = analysisService;
        this.supportService = supportService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PatientMergeExecuteVM executeMerge(
            PatientMergeExecuteDTO request
    ) {
        LOG.debug(
                "Executing patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.getFromPatientId() : null,
                request != null ? request.getToPatientId() : null
        );

        validateRequest(request);

        Patient fromPatient = findPatientOrThrow(
                request.getFromPatientId(),
                "From patient not found"
        );

        Patient toPatient = findPatientOrThrow(
                request.getToPatientId(),
                "To patient not found"
        );

        PatientMergePreviewVM analysis = analysisService.analyze(
                request.getFromPatientId(),
                request.getToPatientId()
        );

        PatientMergeLog mergeLog = createMergeLog(
                fromPatient,
                toPatient,
                request.getReason()
        );

        applyUserDecisions(
                request.getDecisions(),
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

        return PatientMergeExecuteVM.builder()
                .mergeLogId(mergeLog.getId())
                .fromPatientId(fromPatient.getId())
                .toPatientId(toPatient.getId())
                .status("MERGED")
                .build();
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
                .mergeStatus("MERGED")
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

        if (request.getFromPatientId() == null
                || request.getToPatientId() == null) {

            throw new BadRequestAlertException(
                    "Patient IDs are required",
                    "PatientMerge",
                    "ids.required"
            );
        }

        if (request.getFromPatientId().equals(request.getToPatientId())) {
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

    private void applyAutoTransfers(
            PatientMergePreviewVM analysis,
            Long toPatientId,
            Long mergeLogId,
            PatientMergeLog mergeLog
    ) {
        if (analysis == null
                || analysis.getAutoTransfers() == null
                || analysis.getAutoTransfers().isEmpty()) {

            return;
        }

        analysis.getAutoTransfers().forEach(autoTransfer -> {
            applyAutoTransfer(autoTransfer, toPatientId, mergeLogId);
            saveAutoTransferAudit(mergeLog, autoTransfer);
        });
    }

    private void applyDecision(
            PatientMergeDecisionDTO decision,
            Long toPatientId,
            Long mergeLogId
    ) {
        if (decision.getFinalDecision() == null) {
            return;
        }

        switch (decision.getFinalDecision()) {

            case KEEP_TO, IGNORE_FROM_RECORD -> {
            }

            case TAKE_FROM, MANUAL ->
                    updateFieldValue(
                            decision.getTableName(),
                            decision.getFieldName(),
                            decision.getToRecordId(),
                            resolveSelectedValue(decision)
                    );

            case ADD_FROM_RECORD ->
                    moveRecordToTarget(
                            decision.getTableName(),
                            decision.getFromRecordId(),
                            toPatientId,
                            mergeLogId
                    );

            default ->
                    throw new BadRequestAlertException(
                            "Unsupported merge decision",
                            "PatientMerge",
                            "unsupported.decision"
                    );
        }
    }

    private void applyAutoTransfer(
            PatientMergeAutoTransferDTO autoTransfer,
            Long toPatientId,
            Long mergeLogId
    ) {
        if (autoTransfer.getSuggestedDecision()
                == MergeDecision.ADD_FROM_RECORD) {

            moveRecordToTarget(
                    autoTransfer.getTableName(),
                    autoTransfer.getFromRecordId(),
                    toPatientId,
                    mergeLogId
            );

            return;
        }

        if (autoTransfer.getSuggestedDecision()
                == MergeDecision.TAKE_FROM) {

            updateFieldValue(
                    autoTransfer.getTableName(),
                    autoTransfer.getFieldName(),
                    autoTransfer.getToRecordId(),
                    autoTransfer.getSelectedValue()
            );
        }
    }

    private void updateFieldValue(
            String tableName,
            String fieldName,
            Long recordId,
            String selectedValue
    ) {
        if (tableName == null
                || fieldName == null
                || recordId == null) {

            return;
        }

        supportService.validateIdentifier(tableName);
        supportService.validateIdentifier(fieldName);

        PatientMergeTableConfig config =
                supportService.findTableConfig(tableName);

        supportService.validateIdentifier(
                config.getPrimaryKeyColumnName()
        );

        String sql =
                "UPDATE " + tableName
                        + " SET " + fieldName + " = ?"
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        LOG.debug(
                "Updating field value. tableName={}, fieldName={}, recordId={}",
                tableName,
                fieldName,
                recordId
        );

        jdbcTemplate.update(connection -> {

            PreparedStatement ps =
                    connection.prepareStatement(sql);

            supportService.setPreparedStatementValue(
                    ps,
                    1,
                    selectedValue,
                    tableName,
                    fieldName
            );

            ps.setLong(2, recordId);

            return ps;
        });
    }

    private String resolveSelectedValue(
            PatientMergeDecisionDTO decision
    ) {
        if (decision.getFinalDecision() == MergeDecision.TAKE_FROM) {
            return decision.getFromValue();
        }

        return decision.getSelectedValue();
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

        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());
        supportService.validateIdentifier(config.getPatientColumnName());

        saveMovedItemLog(
                mergeLogId,
                config,
                fromRecordId,
                toPatientId
        );

        String sql =
                "UPDATE " + config.getTableName()
                        + " SET " + config.getPatientColumnName() + " = ?"
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        jdbcTemplate.update(sql, toPatientId, fromRecordId);
    }

    private void saveMovedItemLog(
            Long mergeLogId,
            PatientMergeTableConfig config,
            Long recordId,
            Long newPatientId
    ) {
        Long oldPatientId = jdbcTemplate.queryForObject(
                "SELECT " + config.getPatientColumnName()
                        + " FROM " + config.getTableName()
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                Long.class,
                recordId
        );

        Object recordUpdatedAt =
                getRecordUpdatedAt(config, recordId);

        String sql = """
                INSERT INTO patient_merge_item_logs
                (
                    merge_log_id,
                    entity_name,
                    table_name,
                    record_id,
                    old_patient_id,
                    new_patient_id,
                    record_updated_at_at_merge
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                mergeLogId,
                config.getEntityName(),
                config.getTableName(),
                recordId,
                oldPatientId,
                newPatientId,
                recordUpdatedAt
        );
    }

    private Object getRecordUpdatedAt(
            PatientMergeTableConfig config,
            Long recordId
    ) {
        if (config.getUpdatedAtColumnName() == null
                || config.getUpdatedAtColumnName().isBlank()) {

            return null;
        }

        supportService.validateIdentifier(
                config.getUpdatedAtColumnName()
        );

        List<Object> result = jdbcTemplate.query(
                "SELECT " + config.getUpdatedAtColumnName()
                        + " FROM " + config.getTableName()
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                (rs, rowNum) -> rs.getObject(1),
                recordId
        );

        return result.isEmpty()
                ? null
                : result.get(0);
    }

    private void transferEmrRecords(
            Long fromPatientId,
            Long toPatientId,
            Long mergeLogId
    ) {
        List<PatientMergeTableConfig> configs =
                tableConfigRepository
                        .findByEnabledTrueOrderBySortOrderAscIdAsc();

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
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());
        supportService.validateIdentifier(config.getPatientColumnName());

        List<Map<String, Object>> records =
                jdbcTemplate.queryForList(
                        "SELECT " + config.getPrimaryKeyColumnName()
                                + " FROM " + config.getTableName()
                                + " WHERE " + config.getPatientColumnName() + " = ?",
                        fromPatientId
                );

        records.stream()
                .map(record ->
                        supportService.toLong(
                                record.get(config.getPrimaryKeyColumnName())
                        )
                )
                .filter(recordId -> recordId != null)
                .forEach(recordId -> {

                    saveMovedItemLog(
                            mergeLogId,
                            config,
                            recordId,
                            toPatientId
                    );

                    jdbcTemplate.update(
                            "UPDATE " + config.getTableName()
                                    + " SET " + config.getPatientColumnName() + " = ?"
                                    + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                            toPatientId,
                            recordId
                    );
                });
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
                        .entityName(dto.getEntityName())
                        .tableName(dto.getTableName())
                        .fromRecordId(dto.getFromRecordId())
                        .toRecordId(dto.getToRecordId())
                        .matchKey(dto.getMatchKey())
                        .fieldName(dto.getFieldName())
                        .fieldLabel(dto.getFieldLabel())
                        .fromValue(dto.getFromValue())
                        .toValue(dto.getToValue())
                        .suggestedDecision(dto.getSuggestedDecision())
                        .finalDecision(dto.getFinalDecision())
                        .selectedValue(dto.getSelectedValue())
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
                        .entityName(autoTransfer.getEntityName())
                        .tableName(autoTransfer.getTableName())
                        .fromRecordId(autoTransfer.getFromRecordId())
                        .toRecordId(autoTransfer.getToRecordId())
                        .matchKey(autoTransfer.getMatchKey())
                        .fieldName(autoTransfer.getFieldName())
                        .fieldLabel(autoTransfer.getFieldLabel())
                        .fromValue(autoTransfer.getFromValue())
                        .toValue(autoTransfer.getToValue())
                        .suggestedDecision(autoTransfer.getSuggestedDecision())
                        .finalDecision(autoTransfer.getSuggestedDecision())
                        .selectedValue(autoTransfer.getSelectedValue())
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