package com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ReviewOfSystemCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotEmpty String bodySystem,
        @NotEmpty String systemDetail,
        String note
) implements Serializable {}

