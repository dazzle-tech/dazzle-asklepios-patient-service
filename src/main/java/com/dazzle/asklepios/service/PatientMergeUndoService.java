package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeUndoResponse;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional
public class PatientMergeUndoService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeUndoService.class);
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;
    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeUndoService(
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientRepository patientRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.patientRepository = patientRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PatientMergeUndoResponse undoMerge(Long mergeLogId) {
        if (mergeLogId == null) {
            throw new BadRequestAlertException("Merge log id is required", "PatientMerge", "merge.log.id.required");
        }

        PatientMergeLog mergeLog = patientMergeLogRepository.findById(mergeLogId)
                .orElseThrow(() -> new NotFoundAlertException("Merge log not found", "PatientMergeLog", mergeLogId.toString()));

        validateUndoAllowed(mergeLog);

        int restoredFieldsCount = restoreFieldChanges(mergeLog);
        int restoredRecordsCount = restoreMovedRecords(mergeLog);

        restoreSourcePatient(mergeLog);
        markMergeAsUndone(mergeLog);

        return PatientMergeUndoResponse.builder()
                .mergeLogId(mergeLog.getId())
                .fromPatientId(mergeLog.getFromPatient().getId())
                .toPatientId(mergeLog.getToPatient().getId())
                .status("UNDONE")
                .restoredFieldsCount(restoredFieldsCount)
                .restoredRecordsCount(restoredRecordsCount)
                .build();
    }

    private void validateUndoAllowed(PatientMergeLog mergeLog) {

        if (!"MERGED".equals(mergeLog.getMergeStatus())) {
            throw new BadRequestAlertException(
                    "Only MERGED records can be undone",
                    "PatientMerge",
                    "merge.not.active"
            );
        }

        Long newerMerges = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM patient_merge_logs
                WHERE to_patient_id = ?
                  AND merged_at > ?
                  AND merge_status = 'MERGED'
                """,
                Long.class,
                mergeLog.getToPatient().getId(),
                java.sql.Timestamp.from(mergeLog.getMergedAt())
        );

        if (newerMerges != null && newerMerges > 0) {
            throw new BadRequestAlertException(
                    "newer.merges.exist" ,
                    "PatientMerge",
                    "Cannot undo merge because newer merges exist on target patient"
            );
        }
    }

    private int restoreFieldChanges(PatientMergeLog mergeLog) {
        List<PatientMergeMasterDecision> decisions =
                patientMergeMasterDecisionRepository.findByMergeLogId(mergeLog.getId());

        int count = 0;

        for (PatientMergeMasterDecision decision : decisions) {
            if (!shouldRestoreField(decision)) {
                continue;
            }

            restoreField(decision);
            count++;
        }

        return count;
    }

    private boolean shouldRestoreField(PatientMergeMasterDecision decision) {
        if (decision.getFieldName() == null || decision.getFieldName().isBlank()) {
            return false;
        }

        if (!"PATIENT".equals(decision.getEntityName())) {
            return false;
        }

        return decision.getFinalDecision() == MergeDecision.TAKE_FROM
                || decision.getFinalDecision() == MergeDecision.MANUAL;
    }

    private void restoreField(PatientMergeMasterDecision decision) {
        validateIdentifier(decision.getTableName());
        validateIdentifier(decision.getFieldName());

        String primaryKeyColumn = "id";

        String sql = "UPDATE " + decision.getTableName()
                + " SET " + decision.getFieldName() + " = ?"
                + " WHERE " + primaryKeyColumn + " = ?";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);

            setPreparedStatementValue(
                    ps,
                    1,
                    decision.getToValue(),
                    decision.getTableName(),
                    decision.getFieldName()
            );

            ps.setLong(2, decision.getToRecordId());

            return ps;
        });
    }

    private int restoreMovedRecords(PatientMergeLog mergeLog) {
        List<Map<String, Object>> movedItems = jdbcTemplate.queryForList(
                """
                SELECT entity_name, table_name, record_id, old_patient_id, new_patient_id
                FROM patient_merge_item_logs
                WHERE merge_log_id = ?
                """,
                mergeLog.getId()
        );

        int count = 0;

        for (Map<String, Object> item : movedItems) {
            String tableName = String.valueOf(item.get("table_name"));
            Long recordId = toLong(item.get("record_id"));
            Long oldPatientId = toLong(item.get("old_patient_id"));

            restoreMovedRecord(tableName, recordId, oldPatientId);
            count++;
        }

        return count;
    }

    private void restoreMovedRecord(String tableName, Long recordId, Long oldPatientId) {
        validateIdentifier(tableName);

        String sql = "UPDATE " + tableName
                + " SET patient_id = ?"
                + " WHERE id = ?";

        jdbcTemplate.update(sql, oldPatientId, recordId);
    }

    private void restoreSourcePatient(PatientMergeLog mergeLog) {
        Patient fromPatient = mergeLog.getFromPatient();

        fromPatient.setPatientStatus(PatientStatus.ACTIVE);
        fromPatient.setMergedIntoPatientId(null);
        fromPatient.setMergedAt(null);
        fromPatient.setMergedBy(null);
        fromPatient.setMergeNote(null);

        patientRepository.save(fromPatient);
    }

    private void markMergeAsUndone(PatientMergeLog mergeLog) {
        mergeLog.setMergeStatus("UNDONE");
        mergeLog.setUndoneAt(Instant.now());
        mergeLog.setUndoneBy(currentUsername());

        patientMergeLogRepository.save(mergeLog);
    }

    private void setPreparedStatementValue(
            PreparedStatement ps,
            int index,
            Object value,
            String tableName,
            String columnName
    ) throws SQLException {

        if (value == null || value.toString().isBlank()) {
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
            case "date" -> ps.setDate(index, java.sql.Date.valueOf(strValue));

            case "timestamp without time zone",
                 "timestamp with time zone",
                 "timestamp" -> ps.setTimestamp(index, java.sql.Timestamp.valueOf(strValue));

            case "bigint" -> ps.setLong(index, Long.parseLong(strValue));

            case "integer" -> ps.setInt(index, Integer.parseInt(strValue));

            case "boolean" -> ps.setBoolean(index, Boolean.parseBoolean(strValue));

            default -> ps.setString(index, strValue);
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

    private void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BadRequestAlertException("Invalid SQL identifier", "PatientMerge", "invalid.identifier");
        }
    }


    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_order_tests_result",
                        "No authenticated user"
                ));
    }
}