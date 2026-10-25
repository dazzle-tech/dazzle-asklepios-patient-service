package com.dazzle.asklepios.repository.storeProcedure;

import com.dazzle.asklepios.service.dto.storeProcedureDto.IntegrationResultRequestDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DiagnosticOrderTestResultProcedureRepository {

    private final JdbcTemplate jdbcTemplate;

    public DiagnosticOrderTestResultProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertResult(
            IntegrationResultRequestDTO request,
            String marker,
            String normalRangeValue
    ) {
        jdbcTemplate.update(
                "CALL upsert_diagnostic_order_test_result(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                request.getOrderTestId(),
                request.getProfileTestId(),
                request.getResultValueNumber(),
                request.getResultValueText(),
                marker,
                request.getApprovedBy(),
                request.getProcessingStatus(),
                normalRangeValue,
                "integration"
        );
    }
}
