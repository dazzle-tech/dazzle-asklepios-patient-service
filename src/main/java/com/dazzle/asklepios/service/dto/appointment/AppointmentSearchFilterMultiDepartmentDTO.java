package com.dazzle.asklepios.service.dto.appointment;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

import java.util.List;

public record AppointmentSearchFilterMultiDepartmentDTO(
        Long facility,
        List<Long> departmentIds,
        TemplateType resourceType,
        Long resourceId,
        AppointmentStatus status,
        List<BookingMode> bookingMode,
        Long patientId
) {
}