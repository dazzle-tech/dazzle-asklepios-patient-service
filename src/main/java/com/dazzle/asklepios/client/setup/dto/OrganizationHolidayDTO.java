package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.HolidayType;

import java.time.LocalDate;

public record OrganizationHolidayDTO(
        Long id,
        Long organizationDefinitionId,
        String name,
        HolidayType holidayType,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        Boolean isActive,
        Boolean allFacilities,
        String facilityIds,
        Boolean recurring
) {
}
