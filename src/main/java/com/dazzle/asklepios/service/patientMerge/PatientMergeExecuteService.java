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
                "MERGED"
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

            case IGNORE_FROM_RECORD -> {
                if (shouldForceTransferRecord(decision)) {
                    moveRecordToTarget(
                            decision.tableName(),
                            decision.fromRecordId(),
                            toPatientId,
                            mergeLogId
                    );
                }
            }

            case TAKE_FROM, MANUAL ->
                    updateFieldValue(
                            decision.tableName(),
                            decision.fieldName(),
                            decision.toRecordId(),
                            resolveSelectedValue(decision)
                    );

            case ADD_FROM_RECORD ->
                    moveRecordToTarget(
                            decision.tableName(),
                            decision.fromRecordId(),
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

    private boolean shouldForceTransferRecord(
            PatientMergeDecisionDTO decision
    ) {
        if (decision.tableName() == null || decision.fromRecordId() == null) {
            return false;
        }

        if (decision.fieldName() != null && !decision.fieldName().isBlank()) {
            return false;
        }

        PatientMergeTableConfig config =
                supportService.findTableConfig(decision.tableName());

        return config.getMergeCategory() == PatientMergeCategory.EMR;
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
                        .entityName(dto.entityName())
                        .tableName(dto.tableName())
                        .fromRecordId(dto.fromRecordId())
                        .toRecordId(dto.toRecordId())
                        .matchKey(dto.matchKey())
                        .fieldName(dto.fieldName())
                        .fieldLabel(dto.fieldLabel())
                        .fromValue(dto.fromValue())
                        .toValue(dto.toValue())
                        .suggestedDecision(dto.suggestedDecision())
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
                        .suggestedDecision(autoTransfer.suggestedDecision())
                        .finalDecision(autoTransfer.suggestedDecision())
                        .selectedValue(autoTransfer.selectedValue())
                        .build()
        );
    }
    private boolean isEmrTable(String tableName) {
        if (tableName == null) {
            return false;
        }

        PatientMergeTableConfig config = supportService.findTableConfig(tableName);

        return config.getMergeCategory() == PatientMergeCategory.EMR;
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