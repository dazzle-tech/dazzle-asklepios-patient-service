package com.dazzle.asklepios.service.dto.radiology;

import com.dazzle.asklepios.domain.enumeration.Severity;

import java.io.Serializable;

public record DiagnosticOrderTestReportUpdateDTO(
        String report,
        Severity severity
) implements Serializable {
}

