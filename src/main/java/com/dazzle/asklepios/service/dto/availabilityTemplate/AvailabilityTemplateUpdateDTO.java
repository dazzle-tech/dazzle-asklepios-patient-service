package com.dazzle.asklepios.service.dto.availabilityTemplate;

import com.dazzle.asklepios.domain.enumeration.FinancialDetails;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import jakarta.validation.constraints.NotEmpty;
import software.amazon.awssdk.annotations.NotNull;

import java.util.List;

public record AvailabilityTemplateUpdateDTO(

        @NotNull Long id,
        @NotNull Long facilityId,
        @NotNull Long departmentId,
        @NotEmpty String templateName,
        @NotNull TemplateType templateType,
        @NotNull Long resourceId,
        String templateColor,
        @NotNull TemplateStatus status,
        Integer versionNo,

        Long copyFromTemplateId,
        Long parentTemplateId,

        Integer durationMinutes,
        @NotNull Integer defaultBufferBeforeMinutes,
        @NotNull Integer defaultBufferAfterMinutes,
        @NotNull Integer parallelCapacityValue,

        Long defaultServiceId,
        Integer numberOfResourcesExpected,

        @NotNull Boolean requirePractitioner,
        Long defaultPractitionerId,

        @NotNull Boolean requireBilling,
        @NotNull Boolean requirePreAssessment,

        @NotNull Boolean allowPatientPortalBooking,
        @NotNull Boolean requireConfirmation,

        FinancialDetails financialDetails,

        @NotNull Boolean isActive,

        List<WorkingDayJson> workingDays,

        List<AvailabilityTemplateAllowedServiceDTO> allowedServices,

        Boolean allowWalkInBooking


) {}