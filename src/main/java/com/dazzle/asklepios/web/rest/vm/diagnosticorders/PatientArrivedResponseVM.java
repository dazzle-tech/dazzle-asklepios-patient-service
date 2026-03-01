package com.dazzle.asklepios.web.rest.vm.diagnosticorders;

import java.io.Serializable;
import java.time.Instant;

public record PatientArrivedResponseVM(
        Long testId,
        Instant patientArrivedDate,
        String patientArrivedNoteRad
) implements Serializable {}