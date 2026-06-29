package com.dazzle.asklepios.service.patientMerge;

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
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeDataReader;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeRecordTransferService;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeSupportService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PatientMergeAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeAnalysisService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PatientRepository patientRepository;
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeFieldConfigRepository fieldConfigRepository;
    private final PatientMergeSupportService supportService;
    private final PatientMergeDataReader dataReader;
    private final PatientMergeRecordTransferService recordTransferService;

    public PatientMergeAnalysisService(
            PatientRepository patientRepository,
            PatientMergeTableConfigRepository tableConfigRepository,
            PatientMergeFieldConfigRepository fieldConfigRepository,
            PatientMergeSupportService supportService, PatientMergeDataReader dataReader, PatientMergeRecordTransferService recordTransferService
    ) {
        this.patientRepository = patientRepository;
        this.tableConfigRepository = tableConfigRepository;
        this.fieldConfigRepository = fieldConfigRepository;
        this.supportService = supportService;

        this.dataReader = dataReader;
        this.recordTransferService = recordTransferService;
    }
    /**
     * Builds a merge preview between two patients.
     *
     * Preview includes:
     * - Demographic field conflicts (patient master record)
     * - Automatic field transfers when target values are empty
     * - Automatic child record transfers when no matching target record exists
     *
     * Matching child records are skipped and do not generate conflicts.
     */
    public PatientMergePreviewVM analyze(Long fromPatientId, Long toPatientId) {
        LOG.debug("Starting patient merge analysis. fromPatientId={}, toPatientId={}", fromPatientId, toPatientId);

        validateAnalyzeRequest(fromPatientId, toPatientId);

        Patient fromPatient = findPatientOrThrow(fromPatientId, "From patient not found");
        Patient toPatient = findPatientOrThrow(toPatientId, "To patient not found");

        validatePatientCanBeMerged(fromPatient, "fromPatient");
        validatePatientCanBeMerged(toPatient, "toPatient");

        List<PatientMergeConflictDTO> conflicts = new ArrayList<>();
        List<PatientMergeAutoTransferDTO> autoTransfers = new ArrayList<>();

        List<PatientMergeTableConfig> tableConfigs =
                tableConfigRepository.findByEnabledTrue();

        LOG.debug("Loaded {} enabled merge table configs", tableConfigs.size());

        for (PatientMergeTableConfig tableConfig : tableConfigs) {
            analyzeTableConfig(
                    tableConfig,
                    fromPatientId,
                    toPatientId,
                    conflicts,
                    autoTransfers
            );
        }

        LOG.debug(
                "Completed patient merge analysis. fromPatientId={}, toPatientId={}, conflicts={}, autoTransfers={}",
                fromPatientId,
                toPatientId,
                conflicts.size(),
                autoTransfers.size()
        );

        return new PatientMergePreviewVM(
                fromPatientId,
                toPatientId,
                conflicts,
                autoTransfers
        );
    }


    private void analyzeTableConfig(
            PatientMergeTableConfig tableConfig,
            Long fromPatientId,
            Long toPatientId,
            List<PatientMergeConflictDTO> conflicts,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        List<PatientMergeFieldConfig> fieldConfigs =
                resolveFieldConfigs(tableConfig);

        if (tableConfig.getMergeCategory() == PatientMergeCategory.MASTER) {

            if (fieldConfigs.isEmpty()) {
                LOG.debug(
                        "Skipping master table config with no fields. tableName={}",
                        tableConfig.getTableName()
                );
                return;
            }

            if (isPatientMasterTable(tableConfig)) {
                analyzePatientMasterTable(
                        tableConfig,
                        fieldConfigs,
                        fromPatientId,
                        toPatientId,
                        conflicts,
                        autoTransfers
                );
                return;
            }

            analyzeChildTable(
                    tableConfig,
                    fieldConfigs,
                    fromPatientId,
                    toPatientId,

                    autoTransfers
            );
        }
    }
    /**
     * Analyzes patient demographic fields.
     *
     * Differences are reported as conflicts.
     * Empty target values are reported as automatic transfers.
     */
    private void analyzePatientMasterTable(
            PatientMergeTableConfig tableConfig,
            List<PatientMergeFieldConfig> fieldConfigs,
            Long fromPatientId,
            Long toPatientId,
            List<PatientMergeConflictDTO> conflicts,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        Map<String, Object> fromRow = dataReader.loadPatientRow(tableConfig, fromPatientId);
        Map<String, Object> toRow = dataReader.loadPatientRow(tableConfig, toPatientId);

        if (fromRow == null || toRow == null) {
            LOG.debug(
                    "Skipping patient master table because one row is missing. tableName={}, fromRowExists={}, toRowExists={}",
                    tableConfig.getTableName(),
                    fromRow != null,
                    toRow != null
            );
            return;
        }

        for (PatientMergeFieldConfig fieldConfig : fieldConfigs) {
            analyzeField(
                    tableConfig,
                    fieldConfig,
                    fromRow,
                    toRow,
                    fromPatientId,
                    toPatientId,
                    conflicts,
                    autoTransfers
            );
        }
    }
    /**
     * Analyzes child records using configured match keys.
     *
     * Rules:
     * - No matching target record -> auto transfer record.
     * - Matching target record exists -> skip source record.
     *
     * Child tables do not generate field-level conflicts.
     */
    private void analyzeChildTable(
            PatientMergeTableConfig tableConfig,
            List<PatientMergeFieldConfig> fieldConfigs,
            Long fromPatientId,
            Long toPatientId,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        List<Map<String, Object>> fromRows = dataReader.loadRows(tableConfig, fromPatientId);
        List<Map<String, Object>> toRows = dataReader.loadRows(tableConfig, toPatientId);

        List<String> matchKeyColumns = splitColumns(tableConfig.getMatchKeyColumns());

        Map<String, Map<String, Object>> toRowsByKey = toRows.stream()
                .collect(Collectors.toMap(
                        row -> buildMatchKey(row, matchKeyColumns),
                        row -> row,
                        (existing, replacement) -> existing
                ));

        LOG.debug(
                "Analyzing child table. tableName={}, fromRows={}, toRows={}, matchKeyColumns={}",
                tableConfig.getTableName(),
                fromRows.size(),
                toRows.size(),
                matchKeyColumns
        );

        for (Map<String, Object> fromRow : fromRows) {
            String matchKey = buildMatchKey(fromRow, matchKeyColumns);

            Map<String, Object> toRow = toRowsByKey.get(matchKey);

            Long fromRecordId = supportService.toLong(fromRow.get(tableConfig.getPrimaryKeyColumnName()));
            Long toRecordId = toRow != null
                    ? supportService.toLong(toRow.get(tableConfig.getPrimaryKeyColumnName()))
                    : null;

            if (toRow == null) {
                addMissingRecordAutoTransfer(
                        tableConfig,
                        fromRecordId,
                        matchKey,
                        autoTransfers
                );
                continue;
            }

            LOG.debug(
                    "Skipping child record because matching target record exists. tableName={}, fromRecordId={}, toRecordId={}, matchKey={}",
                    tableConfig.getTableName(),
                    fromRecordId,
                    toRecordId,
                    matchKey
            );
        }
    }

    private void analyzeField(
            PatientMergeTableConfig tableConfig,
            PatientMergeFieldConfig fieldConfig,
            Map<String, Object> fromRow,
            Map<String, Object> toRow,
            Long fromRecordId,
            Long toRecordId,
            List<PatientMergeConflictDTO> conflicts,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        String fieldName = fieldConfig.getFieldName();

        Object fromValue = fromRow.get(fieldName);
        Object toValue = toRow.get(fieldName);

        String fromStr = toStringSafe(fromValue);
        String toStr = toStringSafe(toValue);

        boolean fromEmpty = isEmptyValue(fromValue);
        boolean toEmpty = isEmptyValue(toValue);

        if (fromEmpty && toEmpty) {
            return;
        }

        if (toEmpty && !fromEmpty) {
            addAutoTransfer(
                    tableConfig,
                    fieldConfig,
                    fromRow,
                    toRow,
                    fromRecordId,
                    toRecordId,
                    fromStr,
                    autoTransfers
            );
            return;
        }

        if (!toEmpty && !fromEmpty && !normalize(fromStr).equals(normalize(toStr))) {
            addConflict(
                    tableConfig,
                    fieldConfig,
                    fromRow,
                    toRow,
                    fromRecordId,
                    toRecordId,
                    fromStr,
                    toStr,
                    MergeDecision.KEEP_TO,
                    conflicts
            );
        }
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
                new PatientMergeConflictDTO(
                        tableConfig.getEntityName(),
                        tableConfig.getTableName(),
                        fromRecordId,
                        toRecordId,
                        matchKey,
                        fieldConfig.getFieldName(),
                        fieldConfig.getFieldLabel(),
                        fromStr,
                        toStr,
                        suggestedDecision,
                        recordTransferService.getColumnType(
                                tableConfig.getTableName(),
                                fieldConfig.getFieldName()
                        ),
                        fieldConfig.getInputType(),
                        fieldConfig.getInputSource()
                )
        );

        LOG.trace(
                "Added conflict. tableName={}, fieldName={}, fromRecordId={}, toRecordId={}",
                tableConfig.getTableName(),
                fieldConfig.getFieldName(),
                fromRecordId,
                toRecordId
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
        String matchKey = buildMatchKey(
                fromRow,
                splitColumns(tableConfig.getMatchKeyColumns())
        );

        autoTransfers.add(
                new PatientMergeAutoTransferDTO(
                        tableConfig.getEntityName(),
                        tableConfig.getTableName(),
                        fromRecordId,
                        toRecordId,
                        matchKey,
                        fieldConfig.getFieldName(),
                        fieldConfig.getFieldLabel(),
                        fromStr,
                        "",
                        fromStr,
                        MergeDecision.TAKE_FROM,
                        recordTransferService.getColumnType(
                                tableConfig.getTableName(),
                                fieldConfig.getFieldName()
                        ),
                        fieldConfig.getInputType(),
                        fieldConfig.getInputSource()
                )
        );
        LOG.trace(
                "Added auto-transfer. tableName={}, fieldName={}, fromRecordId={}, toRecordId={}",
                tableConfig.getTableName(),
                fieldConfig.getFieldName(),
                fromRecordId,
                toRecordId
        );
    }

    /**
     * Resolves configured fields and optionally appends
     * auto-discovered fields based on table configuration.
     */
    private List<PatientMergeFieldConfig> resolveFieldConfigs(PatientMergeTableConfig tableConfig) {
        List<PatientMergeFieldConfig> configuredFields =
                fieldConfigRepository.findByTableConfigIdAndEnabledTrue(
                        tableConfig.getId()
                );



        List<PatientMergeFieldConfig> autoDiscoveredFields =
                autoDiscoverFields(tableConfig);

        List<String> configuredFieldNames = configuredFields.stream()
                .map(PatientMergeFieldConfig::getFieldName)
                .toList();

        List<PatientMergeFieldConfig> mergedFields = new ArrayList<>();
        mergedFields.addAll(configuredFields);

        for (PatientMergeFieldConfig autoField : autoDiscoveredFields) {
            if (!configuredFieldNames.contains(autoField.getFieldName())) {
                mergedFields.add(autoField);
            }
        }

        LOG.debug(
                "Resolved field configs. tableName={}, configured={}, autoDiscovered={}, total={}",
                tableConfig.getTableName(),
                configuredFields.size(),
                autoDiscoveredFields.size(),
                mergedFields.size()
        );

        return mergedFields;
    }
    /**
     * Creates temporary field definitions from database metadata
     * for tables configured with auto-discovery enabled.
     */
    private List<PatientMergeFieldConfig> autoDiscoverFields(PatientMergeTableConfig tableConfig) {
        List<String> columns = dataReader.loadColumnNames(tableConfig);

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
                            .enabled(true)
                            .build()
            );
        }

        LOG.debug(
                "Auto-discovered {} fields. tableName={}",
                result.size(),
                tableConfig.getTableName()
        );

        return result;
    }

    private Patient findPatientOrThrow(Long patientId, String message) {
        return patientRepository.findById(patientId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                message,
                                "Patient",
                                patientId.toString()
                        )
                );
    }

    private void validateAnalyzeRequest(Long fromPatientId, Long toPatientId) {
        if (fromPatientId == null || toPatientId == null) {
            throw new BadRequestAlertException(
                    "IDs required",
                    "PatientMerge",
                    "ids.required"
            );
        }

        if (fromPatientId.equals(toPatientId)) {
            throw new BadRequestAlertException(
                    "Cannot merge same patient",
                    "PatientMerge",
                    "same.patient"
            );
        }
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

    private boolean isPatientMasterTable(PatientMergeTableConfig tableConfig) {
        return "patients".equals(tableConfig.getTableName())
                || "id".equals(tableConfig.getPatientColumnName());
    }

    private List<String> splitColumns(String columns) {
        if (columns == null || columns.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        for (String col : columns.split(",")) {
            String trimmed = col.trim();

            if (!trimmed.isEmpty()) {
                supportService.validateIdentifier(trimmed);
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

            key.append(toStringSafe(row.get(col)));
        }

        return key.toString();
    }


    /**
     * Converts database column names to user-friendly labels.
     * Example: first_name -> First Name
     */

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

            label.append(Character.toUpperCase(part.charAt(0)));

            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase());
            }
        }

        return label.toString();
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





    private void addMissingRecordAutoTransfer(
            PatientMergeTableConfig tableConfig,
            Long fromRecordId,
            String matchKey,
            List<PatientMergeAutoTransferDTO> autoTransfers
    ) {
        autoTransfers.add(
                new PatientMergeAutoTransferDTO(
                        tableConfig.getEntityName(),
                        tableConfig.getTableName(),
                        fromRecordId,
                        null,
                        matchKey,
                        null,
                        tableConfig.getEntityName(),
                        matchKey,
                        "",
                        matchKey,
                        MergeDecision.ADD_FROM_RECORD,
                        null,
                        null,
                        null
                )
        );

        LOG.debug(
                "Auto-transfer child record. tableName={}, fromRecordId={}, matchKey={}",
                tableConfig.getTableName(),
                fromRecordId,
                matchKey
        );
    }
}