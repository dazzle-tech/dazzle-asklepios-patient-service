package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import com.dazzle.asklepios.repository.PatientMergeTableConfigRepository;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeTableConfigSaveDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeAvailableTableVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTableConfigVM;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class PatientMergeConfigService {
    private final PatientMergeTableConfigRepository tableConfigRepository;
    private final PatientMergeSupportService supportService;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeConfigService(PatientMergeTableConfigRepository tableConfigRepository, PatientMergeSupportService supportService, JdbcTemplate jdbcTemplate) {
        this.tableConfigRepository = tableConfigRepository;
        this.supportService = supportService;
        this.jdbcTemplate = jdbcTemplate;
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

        List<String> availableColumns =
                jdbcTemplate.queryForList(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        ORDER BY ordinal_position
                        """,
                        String.class,
                        config.getTableName()
                );

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

        List<String> patientTables =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT table_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND column_name = 'patient_id'
                        ORDER BY table_name
                        """,
                        String.class
                );

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
    @jakarta.transaction.Transactional
    public Integer syncMissingTables() {

        List<String> patientTables = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'patient_id'
                """,
                String.class
        );

        int inserted = 0;

        for (String tableName : patientTables) {

            boolean exists =
                    tableConfigRepository.existsByTableName(tableName);

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

        return columns.stream()
                .filter(column -> column != null && !column.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<String> getTableColumns(String tableName) {

        supportService.validateIdentifier(tableName);

        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                ORDER BY ordinal_position
                """,
                String.class,
                tableName
        );
    }
}
