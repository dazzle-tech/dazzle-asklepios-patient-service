package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

public record AppointmentFromTemplateSearchFilterDTO(
        Long facility,
        Long department,
        TemplateType resourceType,
        Long resourceId,
        AppointmentStatus status,
        BookingMode bookingMode,
        Long patientId
) {
}