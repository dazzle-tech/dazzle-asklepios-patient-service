package com.dazzle.asklepios.service.dto.patientProcedure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientProcedureSaveDtoJsonTest {

    private static final String LOADED_FIELDS =
            "\"id\":1,\"patientProcedureId\":4,\"createdBy\":\"admin\",\"createdDate\":\"2026-11-05T08:00:00Z\","
                    + "\"lastModifiedBy\":\"admin\",\"lastModifiedDate\":\"2026-11-05T08:00:00Z\",";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void preAssessmentAcceptsARecordLoadedFromTheApi() throws Exception {
        var dto = mapper.readValue(
                "{" + LOADED_FIELDS + "\"heartRate\":80}", PatientProcedurePreAssessmentDTO.class);
        assertEquals(new BigDecimal("80"), dto.heartRate());
    }

    @Test
    void performanceAcceptsARecordLoadedFromTheApi() throws Exception {
        var dto = mapper.readValue(
                "{" + LOADED_FIELDS + "\"observations\":\"ok\"}", PatientProcedurePerformanceDTO.class);
        assertEquals("ok", dto.observations());
    }

    @Test
    void postCareAcceptsARecordLoadedFromTheApi() throws Exception {
        var dto = mapper.readValue(
                "{" + LOADED_FIELDS + "\"painScore4\":true}", PatientProcedurePostCareDTO.class);
        assertEquals(Boolean.TRUE, dto.painScore4());
    }

    @Test
    void postCareKeepsSeveralDiagnoses() throws Exception {
        var dto = mapper.readValue(
                "{\"diagnosisIcdIds\":[5,9,5]}", PatientProcedurePostCareDTO.class);
        assertEquals(Set.of(5L, 9L), dto.diagnosisIcdIds());
    }
}
