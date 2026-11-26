package com.dazzle.asklepios.service.dto.patientActivity;

import java.util.List;

/**
 * Tasks due inside a calendar window, plus the open tasks that were already late
 * before today so the dashboard can keep them in reach while the month changes.
 */
public record PatientTaskCalendarVM(
        List<PatientTaskVM> tasks,
        List<PatientTaskVM> overdue,
        long overdueCount,
        boolean truncated
) {
}
