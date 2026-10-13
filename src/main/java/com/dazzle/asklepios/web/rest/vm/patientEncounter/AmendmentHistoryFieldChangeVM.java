package com.dazzle.asklepios.web.rest.vm.patientEncounter;

public record AmendmentHistoryFieldChangeVM(
        String field,
        String oldValue,
        String newValue
) {
}
