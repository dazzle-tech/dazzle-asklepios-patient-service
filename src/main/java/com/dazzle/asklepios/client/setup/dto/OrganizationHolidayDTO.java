package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.HolidayType;

import java.time.LocalDate;

/**
 * DTO returned from setup-service to represent a normal range row.
 * Used by patient-service to pick the best matching range for a patient.
 */
public record OrganizationHolidayDTO(
        Long id,
        Long organizationDefinitionId,
        String name,
        HolidayType holidayType,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        Boolean allFacilities,
        String facilityIds,
        Boolean recurring
) {
}
