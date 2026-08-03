package com.dazzle.asklepios.service.dto.patientMerge;
import com.dazzle.asklepios.domain.enumeration.AppliesTo;
import com.dazzle.asklepios.domain.enumeration.RuleSeverity;
import com.dazzle.asklepios.domain.enumeration.MergeRuleType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Map;

public record PatientMergeValidationRuleDTO(

        Long id,

        @NotNull
        @Size(max = 100)
        String code,

        @NotNull
        @Size(max = 200)
        String name,

        String entityName,

        String tableName,

        @NotNull
        MergeRuleType ruleType,

        @NotNull
        Map<String, Object> conditionConfig,

        @NotNull
        AppliesTo appliesTo,

        @NotNull
        RuleSeverity severity,

        @NotNull
        @Size(max = 500)
        String message,

        @Size(max = 500)
        String actionRequired,

        Boolean enabled

) implements Serializable {}