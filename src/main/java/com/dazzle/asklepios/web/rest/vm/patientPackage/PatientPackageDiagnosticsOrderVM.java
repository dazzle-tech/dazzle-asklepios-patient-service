package com.dazzle.asklepios.web.rest.vm.patientPackage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * Order several package tests (laboratory / radiology / pathology) in one diagnostic order.
 * Urgency, clinical notes and diagnosis are shared; the reason is per test.
 * testId is needed for category lines (the test picked); for an item line it is the item's own test.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientPackageDiagnosticsOrderVM(
        @NotNull Long encounterId,
        Boolean isUrgent,
        @Size(max = 2000) String notes,
        Long icdDiagnosisId,
        Boolean acceptUncoveredAsCash,
        @NotEmpty List<@Valid Line> items
) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            @NotNull Long itemId,
            Long testId,
            @Size(max = 1000) String reason
    ) implements Serializable {}
}
