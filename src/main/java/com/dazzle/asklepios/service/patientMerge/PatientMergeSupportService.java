package com.dazzle.asklepios.service.patientMerge;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeFieldConfig;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.repository.PatientMergeFieldConfigRepository;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PatientMergeSupportService {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeFieldConfigRepository fieldConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeSupportService(
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeFieldConfigRepository fieldConfigRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.tableConfigRepository = tableConfigRepository;
        this.fieldConfigRepository = fieldConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateIdentifier(String identifier) {
        if (identifier == null
                || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {

            throw new BadRequestAlertException(
                    "Invalid SQL identifier",
                    "PatientMerge",
                    "invalid.identifier"
            );
        }
    }

    public PatientMergeTableConfig findTableConfig(String tableName) {
        validateIdentifier(tableName);

        List<PatientMergeTableConfig> configs =
                tableConfigRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();

        return configs.stream()
                .filter(config -> tableName.equals(config.getTableName()))
                .findFirst()
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "Table config not found",
                                "PatientMerge",
                                "table.config.notfound"
                        )
                );
    }

    public PatientMergeFieldConfig findFieldConfig(
            String tableName,
            String fieldName
    ) {
        if (tableName == null
                || tableName.isBlank()
                || fieldName == null
                || fieldName.isBlank()) {

            return null;
        }

        PatientMergeTableConfig tableConfig = findTableConfig(tableName);

        if (tableConfig.getId() == null) {
            return null;
        }

        return fieldConfigRepository
                .findByTableConfigIdAndEnabledTrueOrderBySortOrderAscIdAsc(
                        tableConfig.getId()
                )
                .stream()
                .filter(config -> fieldName.equals(config.getFieldName()))
                .findFirst()
                .orElse(null);
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

        try {
            return jdbcTemplate.queryForObject(
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
        } catch (Exception e) {
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

    public String buildPatientName(Patient patient) {
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

    public Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

    public String safe(String value) {
        return value == null ? "" : value;
    }
    public List<String> splitColumns(String columns) {

        if (columns == null || columns.isBlank()) {
            return List.of();
        }

        return List.of(columns.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public String buildMatchKey(
            java.util.Map<String, Object> row,
            List<String> columns
    ) {
        if (row == null || columns == null || columns.isEmpty()) {
            return "";
        }

        return columns.stream()
                .map(column -> {
                    Object value = getValueIgnoreCase(row, column);

                    return value == null
                            ? ""
                            : value.toString().trim();
                })
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private Object getValueIgnoreCase(
            java.util.Map<String, Object> row,
            String column
    ) {
        if (row.containsKey(column)) {
            return row.get(column);
        }

        for (String key : row.keySet()) {
            if (key.equalsIgnoreCase(column)) {
                return row.get(key);
            }
        }

        return null;
    }
}