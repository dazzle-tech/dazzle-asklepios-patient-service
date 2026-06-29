package com.dazzle.asklepios.service.patientMerge.helpers;

import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Service
@Transactional
public class PatientMergeRecordTransferHelper {
    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeRecordTransferHelper.class);
    private final JdbcTemplate jdbcTemplate;
    private final PatientMergeSupportHelper supportService;

    public PatientMergeRecordTransferHelper(
            JdbcTemplate jdbcTemplate,
            PatientMergeSupportHelper supportService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supportService = supportService;
    }

    public void moveRecordToTarget(
            PatientMergeTableConfig config,
            Long fromRecordId,
            Long toPatientId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());
        supportService.validateIdentifier(config.getPatientColumnName());

        String sql =
                "UPDATE " + config.getTableName()
                        + " SET " + config.getPatientColumnName() + " = ?"
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        jdbcTemplate.update(sql, toPatientId, fromRecordId);
    }

    public Long getRecordPatientId(
            PatientMergeTableConfig config,
            Long recordId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPatientColumnName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());

        return jdbcTemplate.queryForObject(
                "SELECT " + config.getPatientColumnName()
                        + " FROM " + config.getTableName()
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                Long.class,
                recordId
        );
    }

    public List<Map<String, Object>> getRecordsByPatientId(
            PatientMergeTableConfig config,
            Long patientId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPatientColumnName());

        return jdbcTemplate.queryForList(
                "SELECT * FROM " + config.getTableName()
                        + " WHERE " + config.getPatientColumnName() + " = ?",
                patientId
        );
    }

    public Map<String, Object> getRecordById(
            PatientMergeTableConfig config,
            Long recordId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());

        return jdbcTemplate.queryForMap(
                "SELECT * FROM " + config.getTableName()
                        + " WHERE " + config.getPrimaryKeyColumnName() + " = ?",
                recordId
        );
    }

    public List<Map<String, Object>> getRecordIdsByPatientId(
            PatientMergeTableConfig config,
            Long patientId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPatientColumnName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());

        return jdbcTemplate.queryForList(
                "SELECT " + config.getPrimaryKeyColumnName()
                        + " FROM " + config.getTableName()
                        + " WHERE " + config.getPatientColumnName() + " = ?",
                patientId
        );
    }

    public void saveMovedItemLog(
            Long mergeLogId,
            PatientMergeTableConfig config,
            Long recordId,
            Long oldPatientId,
            Long newPatientId
    ) {
        String sql = """
                INSERT INTO patient_merge_item_logs
                (
                    merge_log_id,
                    entity_name,
                    table_name,
                    record_id,
                    old_patient_id,
                    new_patient_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                mergeLogId,
                config.getEntityName(),
                config.getTableName(),
                recordId,
                oldPatientId,
                newPatientId
        );
    }

    public void updateFieldValue(
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

        jdbcTemplate.update(connection -> {

            PreparedStatement ps =
                    connection.prepareStatement(sql);

            setPreparedStatementValue(
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
    public String getColumnType(
            String tableName,
            String columnName
    ) {
        if (tableName == null
                || tableName.isBlank()
                || columnName == null
                || columnName.isBlank()) {

            return null;
        }

        supportService.validateIdentifier(tableName);
        supportService.validateIdentifier(columnName);

        try {
            return jdbcTemplate.queryForObject(
                    """
                            SELECT data_type
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = ?
                              AND column_name = ?
                            """,
                    String.class,
                    tableName,
                    columnName
            );
        } catch (Exception e) {
            LOG.warn(
                    "Failed to get column type. tableName={}, columnName={}",
                    tableName,
                    columnName,
                    e
            );
            return null;
        }
    }

    public void setPreparedStatementValue(
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

        String dataType = getColumnType(tableName, columnName);

        String strValue = value.toString();

        if (dataType == null) {
            ps.setString(index, strValue);
            return;
        }

        switch (dataType) {

            case "date" -> ps.setDate(
                    index,
                    java.sql.Date.valueOf(parseLocalDate(strValue))
            );

            case "timestamp without time zone",
                 "timestamp" -> ps.setTimestamp(
                    index,
                    Timestamp.valueOf(parseLocalDateTime(strValue))
            );

            case "timestamp with time zone" -> ps.setTimestamp(
                    index,
                    Timestamp.from(parseInstant(strValue))
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
    public boolean shouldSkipTransfer(
            PatientMergeTableConfig config,
            Long fromRecordId,
            Long toPatientId
    ) {
        List<String> matchKeyColumns =
                supportService.splitColumns(config.getMatchKeyColumns());

        if (matchKeyColumns.isEmpty()) {
            return false;
        }

        Map<String, Object> fromRecord =
              getRecordById(config, fromRecordId);

        List<Map<String, Object>> toRecords =
               getRecordsByPatientId(config, toPatientId);

        String fromMatchKey =
                supportService.buildMatchKey(fromRecord, matchKeyColumns);

        for (Map<String, Object> toRecord : toRecords) {
            String toMatchKey =
                    supportService.buildMatchKey(toRecord, matchKeyColumns);

            if (fromMatchKey.equals(toMatchKey)) {
                return true;
            }
        }

        return false;
    }
    private LocalDate parseLocalDate(String value) {
        if (value.contains("T")) {
            return Instant.parse(value).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }

        return LocalDate.parse(value);
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value.contains("T") && value.endsWith("Z")) {
            return Instant.parse(value).atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
        }

        return LocalDateTime.parse(value.replace(" ", "T"));
    }

    private Instant parseInstant(String value) {
        if (value.endsWith("Z")) {
            return Instant.parse(value);
        }

        return LocalDateTime.parse(value.replace(" ", "T"))
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant();
    }
}