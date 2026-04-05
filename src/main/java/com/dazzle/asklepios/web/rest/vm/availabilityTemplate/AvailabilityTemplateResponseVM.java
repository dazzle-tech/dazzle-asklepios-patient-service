package com.dazzle.asklepios.web.rest.vm.availabilityTemplate;

import com.dazzle.asklepios.domain.enumeration.FinancialDetails;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;

import java.util.List;

public record AvailabilityTemplateResponseVM(


    Long id,
    Long facilityId,
    Long departmentId,
    String templateName,
    TemplateType templateType,
    String templateColor,
    TemplateStatus status,
    Integer versionNo,

    Long copyFromTemplateId,
    Long parentTemplateId,

    Integer durationMinutes,
    Integer defaultBufferBeforeMinutes,
    Integer defaultBufferAfterMinutes,
    Integer parallelCapacityValue,

    Long defaultServiceId,
    Integer numberOfResourcesExpected,

    Boolean requirePractitioner,
    Long defaultPractitionerId,

    Boolean requireBilling,
    Boolean requirePreAssessment,

    Boolean allowPatientPortalBooking,
    Boolean requireConfirmation,
    Boolean isActive,

    FinancialDetails financialDetails,

    List<WorkingDayJson> workingDays
){}
