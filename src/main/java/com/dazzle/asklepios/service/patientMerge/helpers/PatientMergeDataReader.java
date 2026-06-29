package com.dazzle.asklepios.service.patientMerge.helpers;

import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PatientMergeDataReader {

    private final JdbcTemplate jdbcTemplate;
    private final PatientMergeSupportHelper supportService;

    public PatientMergeDataReader(
            JdbcTemplate jdbcTemplate,
            PatientMergeSupportHelper supportService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supportService = supportService;
    }

    public List<Map<String, Object>> loadRows(
            PatientMergeTableConfig config,
            Long patientId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPatientColumnName());

        String sql = "SELECT * FROM " + config.getTableName()
                + " WHERE " + config.getPatientColumnName() + " = ?";

        return jdbcTemplate.queryForList(sql, patientId);
    }

    public Map<String, Object> loadPatientRow(
            PatientMergeTableConfig config,
            Long patientId
    ) {
        supportService.validateIdentifier(config.getTableName());
        supportService.validateIdentifier(config.getPrimaryKeyColumnName());

        String sql = "SELECT * FROM " + config.getTableName()
                + " WHERE " + config.getPrimaryKeyColumnName() + " = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, patientId);

        return rows.isEmpty() ? null : rows.get(0);
    }
    public List<String> loadColumnNames(PatientMergeTableConfig tableConfig) {
        supportService.validateIdentifier(tableConfig.getTableName());

        String sql = """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = ?
            ORDER BY ordinal_position
            """;

        return jdbcTemplate.queryForList(
                sql,
                String.class,
                tableConfig.getTableName()
        );
    }

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

    public List<String> getTablesByColumnName(String columnName) {
        supportService.validateIdentifier(columnName);

        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = ?
                ORDER BY table_name
                """,
                String.class,
                columnName
        );
    }


}