package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.domain.enumeration.RuleSeverity;

import java.io.Serializable;

public record ValidationIssueVM(

        String code,
        RuleSeverity severity,
        String message,
        String actionRequired

) implements Serializable {}