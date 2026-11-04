package com.dazzle.asklepios.web.rest.vm;

import java.math.BigDecimal;
import java.util.List;

public record EncounterExecutedServicesVM(
        Long encounterId,
        String encounterNumber,
        String patientName,
        String coverageType,
        BigDecimal calculatedTotal,
        BigDecimal patientTotal,
        BigDecimal insuranceTotal,
        List<EncounterExecutedServiceLineVM> lines
) {
}
