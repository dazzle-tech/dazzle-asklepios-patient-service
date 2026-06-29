package com.dazzle.asklepios.service.patientMerge.helpers;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeFieldConfig;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.repository.PatientMergeFieldConfigRepository;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PatientMergeSupportHelper {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeSupportHelper.class);
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeFieldConfigRepository fieldConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeSupportHelper(
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

        return tableConfigRepository.findByTableNameAndEnabledTrue(tableName)
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
                .findByTableConfigIdAndEnabledTrue(
                        tableConfig.getId()
                )
                .stream()
                .filter(config -> fieldName.equals(config.getFieldName()))
                .findFirst()
                .orElse(null);
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
                .peek(this::validateIdentifier)
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
                .collect(Collectors.joining("|"));
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