package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

import java.util.List;

public record AppointmentFromTemplateSearchFilterDTO(
        Long facility,
        Long department,
        TemplateType resourceType,
        Long resourceId,
        AppointmentStatus status,
        List<BookingMode> bookingMode,
        Long patientId
) {
}