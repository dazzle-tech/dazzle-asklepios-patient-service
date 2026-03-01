package com.dazzle.asklepios.web.rest.vm.radiology;

import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;

import java.io.Serializable;
import java.time.Instant;

public record RadiologyImageStatusResponseVM(
        Long reportId,
        Long orderTestId,
        RadiologyImageStatus imageStatus,
        Instant lastModifiedDate
) implements Serializable {}