package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeFieldConfig;
import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.repository.PatientMergeFieldConfigRepository;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeConflictDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergePreviewResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class PatientMergeAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeAnalysisService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final PatientRepository patientRepository;
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeFieldConfigRepository fieldConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeAnalysisService(
            PatientRepository patientRepository,
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeFieldConfigRepository fieldConfigRepository,
            JdbcTemplate jdbcTemplate

    ) {
        this.patientRepository = patientRepository;
        this.tableConfigRepository = tableConfigRepository;
        this.fieldConfigRepository = fieldConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private String toLabel(String fieldName) {

        if (fieldName == null || fieldName.isBlank()) {
            return "";
        }

        String[] parts = fieldName.split("_");

        StringBuilder label = new StringBuilder();

        for (String part : parts) {

            if (!label.isEmpty()) {
                label.append(" ");
            }

            label.append(
                    Character.toUpperCase(part.charAt(0))
            );

            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase());
            }
        }

        return label.toString();
    }

    private List<PatientMergeFieldConfig> autoDiscoverFields(PatientMergeTableConfig tableConfig) {

        validateIdentifier(tableConfig.getTableName());

        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = ?
                ORDER BY ordinal_position
                """;

        List<String> columns = jdbcTemplate.queryForList(
                sql,
                String.class,
                tableConfig.getTableName()
        );

        List<String> excluded = splitColumns(tableConfig.getExcludedColumns());

        List<PatientMergeFieldConfig> result = new ArrayList<>();

        for (String column : columns) {

            if (excluded.contains(column)) {
                continue;
            }

            if (column.equals(tableConfig.getPrimaryKeyColumnName())) {
                continue;
            }

            if (column.equals(tableConfig.getPatientColumnName())) {
                continue;
            }

            result.add(
                    PatientMergeFieldConfig.builder()
                            .fieldName(column)
                            .fieldLabel(toLabel(column))
                            .suggestedDecision(MergeDecision.MANUAL)
                            .enabled(true)
                            .build()
            );
        }

        return result;
    }

    private List<PatientMergeFieldConfig> resolveFieldConfigs(PatientMergeTableConfig tableConfig) {

        List<PatientMergeFieldConfig> configs =
                fieldConfigRepository.findByTableConfigIdAndEnabledTrueOrderBySortOrderAscIdAsc(
                        tableConfig.getId()
                );

        if (!configs.isEmpty()) {
            return configs;
        }

        if (Boolean.TRUE.equals(tableConfig.getAutoDiscoverFields())) {
            return
                    autoDiscoverFields(tableConfig);
        }

        return new ArrayList<>();
    }

    public PatientMergePreviewResponse analyze(Long fromPatientId, Long toPatientId) {
        LOG.debug("Analyzing merge from patient {} to patient {}", fromPatientId, toPatientId);

        if (fromPatientId == null || toPatientId == null) {
            throw new BadRequestAlertException("IDs required", "PatientMerge", "ids.required");
        }

        if (fromPatientId.equals(toPatientId)) {
            throw new BadRequestAlertException("Cannot merge same patient", "PatientMerge", "same.patient");
        }

        Patient fromPatient = patientRepository.findById(fromPatientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "From patient not found",
                        "Patient",
                        fromPatientId.toString()
                ));

        Patient toPatient = patientRepository.findById(toPatientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "To patient not found",
                        "Patient",
                        toPatientId.toString()
                ));

        validatePatientCanBeMerged(fromPatient, "fromPatient");
        validatePatientCanBeMerged(toPatient, "toPatient");
        List<PatientMergeConflictDTO> conflicts = new ArrayList<>();
        List<PatientMergeAutoTransferDTO> autoTransfers = new ArrayList<>();

        List<PatientMergeTableConfig> tableConfigs = tableConfigRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();

        for (PatientMergeTableConfig tableConfig : tableConfigs) {

            if (tableConfig.getMergeCategory() != PatientMergeCategory.MASTER) {
                continue;
            }

            List<PatientMergeFieldConfig> fieldConfigs = resolveFieldConfigs(tableConfig);

            if (fieldConfigs.isEmpty()) {
                continue;
            }

            if ("patients".equals(tableConfig.getTableName()) || "id".equals(tableConfig.getPatientColumnName())) {
                Map<String, Object> fromRow = loadPatientRow(tableConfig, fromPatientId);
                Map<String, Object> toRow = loadPatientRow(tableConfig, toPatientId);

                if (fromRow != null && toRow != null) {
                    for (PatientMergeFieldConfig fieldConfig : fieldConfigs) {
                        analyzeField(
                                tableConfig,
                                fieldConfig,
                                fromRow,
                                toRow,
                                fromPatientId,
                                toPatientId,
                                fromPatientId,
                                toPatientId,
                                conflicts,
                                autoTransfers
                        );
                    }
                }

            } else {
                List<Map<String, Object>> fromRows = loadRows(tableConfig, fromPatientId);
                List<Map<String, Object>> toRows = loadRows(tableConfig, toPatientId);

                List<String> matchKeyColumns = splitColumns(tableConfig.getMatchKeyColumns());

                for (Map<String, Object> fromRow : fromRows) {
                    String matchKey = buildMatchKey(fromRow, matchKeyColumns);
                    Map<String, Object> toRow = findMatchingRow(toRows, matchKey, matchKeyColumns);

                    Long fromRecordId = toLong(fromRow.get(tableConfig.getPrimaryKeyColumnName()));
                    Long toRecordId = toRow != null
                            ? toLong(toRow.get(tableConfig.getPrimaryKeyColumnName()))
                            : null;

                    if (toRow != null) {
                        for (PatientMergeFieldConfig fieldConfig : fieldConfigs) {
                            analyzeField(
                                    tableConfig,
                                    fieldConfig,
                                    fromRow,
                                    toRow,
                                    fromPatientId,
                                    toPatientId,
                                    fromRecordId,
                                    toRecordId,
                                    conflicts,
                                    autoTransfers
                            );
                        }
                    } else {
                        addMissingRecordConflict(
                                tableConfig,
                                fromRecordId,
                                matchKey,
                                conflicts
                        );
                    }
                }
            }
        }
        return PatientMergePreviewResponse.builder()
                .fromPatientId(fromPatientId)
                .toPatientId(toPatientId)
                .conflicts(conflicts)
                .autoTransfers(autoTransfers)
                .build();
    }

    private void analyzeField(
            PatientMergeTableConfig tableConfig,
            PatientMergeFieldConfig fieldConfig,
            Map<String, Object> fromRow,
            Map<String, Object> toRow,
            Long fromPatientId,
            Long toPatientId,
            Long fromRecordId,
            Long toRecordId,
            List<PatientMergeConflictDTO> conflicts,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        String fieldName = fieldConfig.getFieldName();
        String fieldLabel = fieldConfig.getFieldLabel();

        Object fromValue = fromRow.get(fieldName);
        Object toValue = toRow.get(fieldName);

        String fromStr = toStringSafe(fromValue);
        String toStr = toStringSafe(toValue);

        boolean fromEmpty = isEmptyValue(fromValue);
        boolean toEmpty = isEmptyValue(toValue);

        // Both empty or target has value and source is empty - ignore
        if (fromEmpty && toEmpty) {
            return;
        }
        if (toEmpty && !fromEmpty) {
            // Auto-transfer: source has value, target is empty
            addAutoTransfer(tableConfig, fieldConfig, fromRow, toRow, fromRecordId, toRecordId, fromStr, autoTransfers);
        } else if (!toEmpty && !fromEmpty && !normalize(fromStr).equals(normalize(toStr))) {
            // Conflict: both have different values
            addConflict(tableConfig, fieldConfig, fromRow, toRow, fromRecordId, toRecordId, fromStr, toStr, fieldConfig.getSuggestedDecision(), conflicts);
        }
        // else: source empty and target has value - ignore
    }


    private void addConflict(
            PatientMergeTableConfig tableConfig,
            PatientMergeFieldConfig fieldConfig,
            Map<String, Object> fromRow,
            Map<String, Object> toRow,
            Long fromRecordId,
            Long toRecordId,
            String fromStr,
            String toStr,
            MergeDecision suggestedDecision,
            List<PatientMergeConflictDTO> conflicts
    ) {
        String matchKey = buildMatchKey(
                fromRow,
                splitColumns(tableConfig.getMatchKeyColumns())
        );
        conflicts.add(
                PatientMergeConflictDTO.builder()
                        .entityName(tableConfig.getEntityName())
                        .tableName(tableConfig.getTableName())
                        .fromRecordId(fromRecordId)
                        .toRecordId(toRecordId)
                        .matchKey(matchKey)
                        .fieldName(fieldConfig.getFieldName())
                        .fieldLabel(fieldConfig.getFieldLabel())
                        .fromValue(fromStr)
                        .toValue(toStr)
                        .fieldType(getColumnType(tableConfig.getTableName(), fieldConfig.getFieldName()))
                        .suggestedDecision(suggestedDecision)
                        .build()
        );
    }

    private void addAutoTransfer(
            PatientMergeTableConfig tableConfig,
            PatientMergeFieldConfig fieldConfig,
            Map<String, Object> fromRow,
            Map<String, Object> toRow,
            Long fromRecordId,
            Long toRecordId,
            String fromStr,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        String matchKey = buildMatchKey(fromRow, splitColumns(tableConfig.getMatchKeyColumns()));

        autoTransfers.add(PatientMergeAutoTransferDTO.builder()
                .entityName(tableConfig.getEntityName())
                .tableName(tableConfig.getTableName())
                .fromRecordId(fromRecordId)
                .toRecordId(toRecordId)
                .matchKey(matchKey)
                .fieldName(fieldConfig.getFieldName())
                .fieldLabel(fieldConfig.getFieldLabel())
                .fromValue(fromStr)
                .toValue("")
                .selectedValue(fromStr)
                .fieldType(getColumnType(tableConfig.getTableName(), fieldConfig.getFieldName()))
                .suggestedDecision(MergeDecision.TAKE_FROM)
                .build());
    }

    private String toStringSafe(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof LocalDate localDate) {
            return localDate.format(DATE_FORMATTER);
        }

        return value.toString();
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String str) {
            return str.trim().isEmpty();
        }

        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            LOG.warn("Failed to convert value to Long: {}", value, e);
            return null;
        }
    }

    private Map<String, Object> loadPatientRow(PatientMergeTableConfig config, Long patientId) {
        validateIdentifier(config.getTableName());
        validateIdentifier(config.getPrimaryKeyColumnName());

        String sql = "SELECT * FROM " + config.getTableName() + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, patientId);

        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> loadRows(PatientMergeTableConfig config, Long patientId) {
        validateIdentifier(config.getTableName());
        validateIdentifier(config.getPatientColumnName());

        String sql = "SELECT * FROM " + config.getTableName() + " WHERE " + config.getPatientColumnName() + " = ?";
        return jdbcTemplate.queryForList(sql, patientId);
    }

    private List<String> splitColumns(String columns) {
        if (columns == null || columns.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (String col : columns.split(",")) {
            String trimmed = col.trim();
            if (!trimmed.isEmpty()) {
                validateIdentifier(trimmed);
                result.add(trimmed);
            }
        }
        return result;
    }

    private String buildMatchKey(Map<String, Object> row, List<String> matchKeyColumns) {
        if (matchKeyColumns.isEmpty()) {
            return "";
        }

        StringBuilder key = new StringBuilder();
        for (String col : matchKeyColumns) {
            if (!key.isEmpty()) {
                key.append("|");
            }
            Object value = row.get(col);
            key.append(toStringSafe(value));
        }
        return key.toString();
    }

    private Map<String, Object> findMatchingRow(List<Map<String, Object>> rows, String matchKey, List<String> matchKeyColumns) {
        if (matchKeyColumns.isEmpty()) {
            return null;
        }

        for (Map<String, Object> row : rows) {
            String rowKey = buildMatchKey(row, matchKeyColumns);
            if (matchKey.equals(rowKey)) {
                return row;
            }
        }
        return null;
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BadRequestAlertException("Invalid SQL identifier", "PatientMerge", "invalid.identifier");
        }
    }

    private void addMissingRecordConflict(
            PatientMergeTableConfig tableConfig,
            Long fromRecordId,
            String matchKey,
            List<PatientMergeConflictDTO> conflicts
    ) {

        conflicts.add(
                PatientMergeConflictDTO.builder()
                        .entityName(tableConfig.getEntityName())
                        .tableName(tableConfig.getTableName())
                        .fromRecordId(fromRecordId)
                        .toRecordId(null)
                        .matchKey(matchKey)
                        .fieldName(null)
                        .fieldLabel(tableConfig.getEntityName())
                        .fromValue(matchKey)
                        .toValue("")
                        .suggestedDecision(MergeDecision.ADD_FROM_RECORD)
                        .build()
        );
    }

    private String getColumnType(String tableName, String columnName) {
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
    }

    private void validatePatientCanBeMerged(Patient patient, String role) {

        if (patient.getPatientStatus() == PatientStatus.MERGED) {
            throw new BadRequestAlertException(
                    role + ".already.merged",
                    "PatientMerge",
                    role + " is already merged and cannot be used in another merge"

            );
        }
    }
}

