package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.commands;

import org.wildfly.common.annotation.NotNull;

public record DiagnosticTestRequestLinkDiagnosticTestDTO(
        @NotNull Long diagnosticTestId
) {}
