package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;

import java.math.BigDecimal;
import java.util.List;
import java.util.TimeZone;

public record OrganizationDefinitionDTO(
        Long id,
        String name,
        String description,
        String address,
        String contactName,
        String contactAddress,
        String contactEmail,
        String contactMobile,
        String contactLandNumber,
        BigDecimal taxValue,
        TimeZone defaultTimeZone,
        Long defaultLanguageId,
        String defaultLanguageName,
        List<WorkingDayJson> workingDays
) {
}
