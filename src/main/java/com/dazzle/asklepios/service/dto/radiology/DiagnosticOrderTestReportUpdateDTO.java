package com.dazzle.asklepios.service.dto.radiology;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

public record DiagnosticOrderTestReportUpdateDTO(
        String report,

        @Size(max = 50) String severity,

        @Size(max = 50) String approvedBy,
        Instant approvedDate,

        @Size(max = 50) String rejectedBy,
        Instant rejectedDate,

        @Size(max = 500) String rejectedReason,

        @Size(max = 50) String reviewBy,
        Instant reviewDate,

        DiagnosticStatus processingStatus,
        RadiologyImageStatus imageStatus
) implements Serializable {}

