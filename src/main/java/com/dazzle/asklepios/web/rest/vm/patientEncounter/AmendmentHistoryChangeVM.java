package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.AmendmentHistoryAction;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AmendmentHistoryChangeVM(
        String module,
        String medicalSheet,
        AmendmentHistoryAction action,
        Long recordId,
        String changedBy,
        Instant changedAt,
        List<AmendmentHistoryFieldChangeVM> changes,
        Map<String, Object> before,
        Map<String, Object> after
) {
}
