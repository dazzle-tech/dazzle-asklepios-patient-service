package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeDecisionDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteRequest;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteResponse;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergePreviewResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import java.util.Map;
@Service
@Transactional
public class PatientMergeExecuteService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeExecuteService.class);
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final PatientRepository patientRepository;
    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeAnalysisService analysisService;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeExecuteService(
            PatientRepository patientRepository,
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeAnalysisService analysisService,
            JdbcTemplate jdbcTemplate
    ) {
        this.patientRepository = patientRepository;
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.tableConfigRepository = tableConfigRepository;
        this.analysisService = analysisService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PatientMergeExecuteResponse executeMerge(PatientMergeExecuteRequest request) {
        LOG.debug("Executing merge from patient {} to patient {}", request.getFromPatientId(), request.getToPatientId());

        validateRequest(request);

        Patient fromPatient = patientRepository.findById(request.getFromPatientId())
                .orElseThrow(() -> new NotFoundAlertException("From patient not found", "Patient", request.getFromPatientId().toString()));

        Patient toPatient = patientRepository.findById(request.getToPatientId())
                .orElseThrow(() -> new NotFoundAlertException("To patient not found", "Patient", request.getToPatientId().toString()));

        // Get backend-calculated analysis (conflicts and autoTransfers)
        PatientMergePreviewResponse analysis = analysisService.analyze(request.getFromPatientId(), request.getToPatientId());

        PatientMergeLog mergeLog = createMergeLog(fromPatient, toPatient, request.getReason());

        // Apply user decisions
        if (request.getDecisions() != null) {
            for (PatientMergeDecisionDTO decision : request.getDecisions()) {
                saveMasterDecision(mergeLog, decision);
                applyDecision(decision, toPatient.getId(), mergeLog.getId());
            }
        }

        // Apply backend-calculated autoTransfers automatically
        if (analysis.getAutoTransfers() != null) {
            for (PatientMergeAutoTransferDTO autoTransfer : analysis.getAutoTransfers()) {
                // Convert to decision-like object and apply
                applyAutoTransfer(autoTransfer, toPatient.getId(), mergeLog.getId());
                // Save audit record
                saveAutoTransferAudit(mergeLog, autoTransfer);
            }
        }
        transferEmrRecords(fromPatient.getId(), toPatient.getId(), mergeLog.getId());
        markSourcePatientAsMerged(fromPatient, toPatient);

        return PatientMergeExecuteResponse.builder()
                .mergeLogId(mergeLog.getId())
                .fromPatientId(fromPatient.getId())
                .toPatientId(toPatient.getId())
                .status("MERGED")
                .build();
    }

    private void validateRequest(PatientMergeExecuteRequest request) {
        if (request == null) {
            throw new BadRequestAlertException("Request is required", "PatientMerge", "request.required");
        }

        if (request.getFromPatientId() == null || request.getToPatientId() == null) {
            throw new BadRequestAlertException("Patient IDs are required", "PatientMerge", "ids.required");
        }

        if (request.getFromPatientId().equals(request.getToPatientId())) {
            throw new BadRequestAlertException("Cannot merge same patient", "PatientMerge", "same.patient");
        }
    }

    private PatientMergeLog createMergeLog(Patient fromPatient, Patient toPatient, String reason) {
        Instant now = Instant.now();

        PatientMergeLog mergeLog = PatientMergeLog.builder()
                .fromPatient(fromPatient)
                .toPatient(toPatient)
                .mergeStatus("MERGED")
                .mergedBy("system")
                .mergedAt(now)
                .reason(reason)
                .build();

        return patientMergeLogRepository.save(mergeLog);
    }

    private void saveMasterDecision(PatientMergeLog mergeLog, PatientMergeDecisionDTO dto) {
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

    private void saveAutoTransferAudit(PatientMergeLog mergeLog, PatientMergeAutoTransferDTO autoTransfer) {
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

    private void applyDecision(PatientMergeDecisionDTO decision, Long toPatientId, Long mergeLogId) {
        if (decision.getFinalDecision() == null) {
            return;
        }

        switch (decision.getFinalDecision()) {
            case KEEP_TO:
            case IGNORE_FROM_RECORD:
                return;

            case TAKE_FROM:
            case MANUAL:
                applyFieldDecision(decision);
                return;

            case ADD_FROM_RECORD:
                moveRecordToTarget(decision, toPatientId, mergeLogId);
                return;

            default:
                throw new BadRequestAlertException("Unsupported merge decision", "PatientMerge", "unsupported.decision");
        }
    }

    private void applyAutoTransfer(PatientMergeAutoTransferDTO autoTransfer, Long toPatientId, Long mergeLogId) {
        if (autoTransfer.getSuggestedDecision() == MergeDecision.ADD_FROM_RECORD) {
            // Move entire record
            moveRecordToTargetForAutoTransfer(autoTransfer, toPatientId, mergeLogId);
        } else if (autoTransfer.getSuggestedDecision() == MergeDecision.TAKE_FROM) {
            // Update field with value from source
            applyFieldDecisionForAutoTransfer(autoTransfer);
        }
    }

    private void applyFieldDecision(PatientMergeDecisionDTO decision) {
        if (decision.getTableName() == null || decision.getFieldName() == null || decision.getToRecordId() == null) {
            return;
        }

        validateIdentifier(decision.getTableName());
        validateIdentifier(decision.getFieldName());

        PatientMergeTableConfig config = findTableConfig(decision.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());

        String selectedValue = resolveSelectedValue(decision);

        String sql = "UPDATE " + decision.getTableName()
                + " SET " + decision.getFieldName() + " = ?"
                + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        jdbcTemplate.update(
                connection -> {

                    PreparedStatement ps = connection.prepareStatement(sql);

                    setPreparedStatementValue(
                            ps,
                            1,
                            selectedValue,
                            decision.getTableName(),
                            decision.getFieldName()
                    );

                    ps.setLong(2, decision.getToRecordId());

                    return ps;
                }
        );    }

    private void applyFieldDecisionForAutoTransfer(PatientMergeAutoTransferDTO autoTransfer) {
        if (autoTransfer.getTableName() == null || autoTransfer.getFieldName() == null || autoTransfer.getToRecordId() == null) {
            return;
        }

        validateIdentifier(autoTransfer.getTableName());
        validateIdentifier(autoTransfer.getFieldName());

        PatientMergeTableConfig config = findTableConfig(autoTransfer.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());

        String sql = "UPDATE " + autoTransfer.getTableName()
                + " SET " + autoTransfer.getFieldName() + " = ?"
                + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        jdbcTemplate.update(
                connection -> {

                    PreparedStatement ps = connection.prepareStatement(sql);

                    setPreparedStatementValue(
                            ps,
                            1,
                            autoTransfer.getSelectedValue(),
                            autoTransfer.getTableName(),
                            autoTransfer.getFieldName()
                    );

                    ps.setLong(2, autoTransfer.getToRecordId());

                    return ps;
                }
        );    }

    private String resolveSelectedValue(PatientMergeDecisionDTO decision) {
        if (decision.getFinalDecision() == MergeDecision.TAKE_FROM) {
            return decision.getFromValue();
        }

        if (decision.getFinalDecision() == MergeDecision.MANUAL) {
            return decision.getSelectedValue();
        }

        return decision.getSelectedValue();
    }

    private void moveRecordToTarget(PatientMergeDecisionDTO decision, Long toPatientId, Long mergeLogId) {
        if (decision.getTableName() == null || decision.getFromRecordId() == null) {
            return;
        }

        PatientMergeTableConfig config = findTableConfig(decision.getTableName());

        validateIdentifier(config.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());
        validateIdentifier(config.getPatientColumnName());

        String sql = "UPDATE " + config.getTableName()
                + " SET " + config.getPatientColumnName() + " = ?"
                + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";
        saveMovedItemLog(mergeLogId, config, decision.getFromRecordId(), toPatientId);
        jdbcTemplate.update(sql, toPatientId, decision.getFromRecordId());
    }

    private void moveRecordToTargetForAutoTransfer(PatientMergeAutoTransferDTO autoTransfer, Long toPatientId, Long mergeLogId) {
        if (autoTransfer.getTableName() == null || autoTransfer.getFromRecordId() == null) {
            return;
        }

        PatientMergeTableConfig config = findTableConfig(autoTransfer.getTableName());

        validateIdentifier(config.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());
        validateIdentifier(config.getPatientColumnName());

        String sql = "UPDATE " + config.getTableName()
                + " SET " + config.getPatientColumnName() + " = ?"
                + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";
        saveMovedItemLog(mergeLogId, config, autoTransfer.getFromRecordId(), toPatientId);
        jdbcTemplate.update(sql, toPatientId, autoTransfer.getFromRecordId());
    }

    private PatientMergeTableConfig findTableConfig(String tableName) {
        validateIdentifier(tableName);

        List<PatientMergeTableConfig> configs = tableConfigRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();

        return configs.stream()
                .filter(config -> tableName.equals(config.getTableName()))
                .findFirst()
                .orElseThrow(() -> new BadRequestAlertException("Table config not found", "PatientMerge", "table.config.notfound"));
    }

    private void markSourcePatientAsMerged(Patient fromPatient, Patient toPatient) {
        fromPatient.setPatientStatus(PatientStatus.MERGED);
        fromPatient.setMergedIntoPatientId(toPatient.getId());
        fromPatient.setMergedAt(Instant.now());
        String username=currentUsername();
        fromPatient.setMergedBy(username);
        patientRepository.save(fromPatient);
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BadRequestAlertException("Invalid SQL identifier", "PatientMerge", "invalid.identifier");
        }
    }
    private void setPreparedStatementValue(
            PreparedStatement ps,
            int index,
            Object value,
            String tableName,
            String columnName
    ) throws SQLException {

        if (value == null) {
            ps.setObject(index, null);
            return;
        }

        String dataType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );

        String strValue = value.toString();

        switch (dataType) {

            case "date" -> ps.setDate(
                    index,
                    java.sql.Date.valueOf(strValue)
            );

            case "timestamp without time zone",
                 "timestamp with time zone",
                 "timestamp" -> ps.setTimestamp(
                    index,
                    java.sql.Timestamp.valueOf(strValue)
            );

            case "bigint" -> ps.setLong(
                    index,
                    Long.parseLong(strValue)
            );

            case "integer" -> ps.setInt(
                    index,
                    Integer.parseInt(strValue)
            );

            case "boolean" -> ps.setBoolean(
                    index,
                    Boolean.parseBoolean(strValue)
            );

            default -> ps.setString(index, strValue);
        }
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

        Object recordUpdatedAt = null;

        if (config.getUpdatedAtColumnName() != null && !config.getUpdatedAtColumnName().isBlank()) {
            validateIdentifier(config.getUpdatedAtColumnName());

            List<Object> result = jdbcTemplate.query(
                    "SELECT " + config.getUpdatedAtColumnName()
                            + " FROM " + config.getTableName()
                            + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                    (rs, rowNum) -> rs.getObject(1),
                    recordId
            );

            if (!result.isEmpty()) {
                recordUpdatedAt = result.get(0);
            }
        }

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

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests_result",
                        "No authenticated user"
                ));
    }

    private void transferEmrRecords(Long fromPatientId, Long toPatientId, Long mergeLogId) {
        List<PatientMergeTableConfig> configs =
                tableConfigRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();

        for (PatientMergeTableConfig config : configs) {
            if (config.getMergeCategory() != PatientMergeCategory.EMR) {
                continue;
            }

            transferEmrTable(config, fromPatientId, toPatientId, mergeLogId);
        }
    }

    private void transferEmrTable(
            PatientMergeTableConfig config,
            Long fromPatientId,
            Long toPatientId,
            Long mergeLogId
    ) {
        validateIdentifier(config.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());
        validateIdentifier(config.getPatientColumnName());

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT " + config.getPrimaryKeyColumnName()
                        + " FROM " + config.getTableName()
                        + " WHERE " + config.getPatientColumnName() + " = ?",
                fromPatientId
        );

        for (Map<String, Object> record : records) {
            Long recordId = toLong(record.get(config.getPrimaryKeyColumnName()));

            if (recordId == null) {
                continue;
            }

            saveMovedItemLog(mergeLogId, config, recordId, toPatientId);

            jdbcTemplate.update(
                    "UPDATE " + config.getTableName()
                            + " SET " + config.getPatientColumnName() + " = ?"
                            + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                    toPatientId,
                    recordId
            );
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }
}