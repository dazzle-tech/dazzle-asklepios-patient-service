package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeTableConfigSaveDTO;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeDataReader;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeSupportHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeAvailableTableVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTableConfigVM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class PatientMergeConfigService {
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeSupportHelper supportService;
    private final PatientMergeDataReader dataReader;
    public PatientMergeConfigService(PatientMergeTableConfigRepository tableConfigRepository, PatientMergeSupportHelper supportService, PatientMergeDataReader dataReader) {
        this.tableConfigRepository = tableConfigRepository;
        this.supportService = supportService;
        this.dataReader = dataReader;
    }

    @Transactional(readOnly = true)
    public List<PatientMergeTableConfigVM> getTableConfigs() {

        List<PatientMergeTableConfig> configs =
                tableConfigRepository.findAll();

        return configs.stream()
                .map(this::toVm)
                .toList();
    }
    private PatientMergeTableConfigVM toVm(
            PatientMergeTableConfig config
    ) {
        List<String> availableColumns = dataReader.getTableColumns(config.getTableName());

        return new PatientMergeTableConfigVM(
                config.getId(),
                config.getEntityName(),
                config.getTableName(),
                config.getPrimaryKeyColumnName(),
                config.getPatientColumnName(),
                config.getEnabled(),
                config.getMergeCategory() != null
                        ? config.getMergeCategory().name()
                        : null,
                supportService.splitColumns(
                        config.getMatchKeyColumns()
                ),
                supportService.splitColumns(
                        config.getExcludedColumns()
                ),
                availableColumns
        );
    }

    @Transactional(readOnly = true)
    public List<PatientMergeAvailableTableVM> getAvailablePatientTables() {

        List<String> patientTables = dataReader.getTablesByColumnName("patient_id");

        List<String> configuredTables =
                tableConfigRepository.findAll()
                        .stream()
                        .map(PatientMergeTableConfig::getTableName)
                        .toList();

        return patientTables.stream()
                .map(tableName ->
                        new PatientMergeAvailableTableVM(
                                tableName,
                                configuredTables.contains(tableName)
                        )
                )
                .toList();
    }

    @Transactional
    public Integer syncMissingTables() {

        List<String> patientTables = dataReader.getTablesByColumnName("patient_id");

        int inserted = 0;

        for (String tableName : patientTables) {
            boolean exists = tableConfigRepository.existsByTableName(tableName);

            if (exists) {
                continue;
            }

            PatientMergeTableConfig config =
                    PatientMergeTableConfig.builder()
                            .entityName(tableName.toUpperCase())
                            .tableName(tableName)
                            .primaryKeyColumnName("id")
                            .patientColumnName("patient_id")
                            .enabled(false)
                            .mergeCategory(PatientMergeCategory.EMR)
                            .build();

            tableConfigRepository.save(config);

            inserted++;
        }

        return inserted;
    }

    @Transactional
    public PatientMergeTableConfigVM saveTableConfig(PatientMergeTableConfigSaveDTO dto) {

        PatientMergeTableConfig config =
                dto.id() != null
                        ? tableConfigRepository.findById(dto.id())
                        .orElseThrow(() -> new BadRequestAlertException(
                                "Table config not found",
                                "PatientMerge",
                                "table.config.notfound"
                        ))
                        : new PatientMergeTableConfig();

        config.setEntityName(dto.entityName());
        config.setTableName(dto.tableName());
        config.setPrimaryKeyColumnName(dto.primaryKeyColumnName());
        config.setPatientColumnName(dto.patientColumnName());
        config.setEnabled(Boolean.TRUE.equals(dto.enabled()));
        config.setMergeCategory(PatientMergeCategory.valueOf(dto.mergeCategory()));

        config.setMatchKeyColumns(joinColumns(dto.matchKeyColumns()));
        config.setExcludedColumns(joinColumns(dto.excludedColumns()));

        return toVm(tableConfigRepository.save(config));
    }

    private String joinColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }

        String result = columns.stream()
                .filter(column -> column != null && !column.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));

        return result.isBlank() ? null : result;
    }

    @Transactional(readOnly = true)
    public List<String> getTableColumns(String tableName) {

        return dataReader.getTableColumns(tableName);
    }
}
