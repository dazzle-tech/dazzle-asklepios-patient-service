package com.dazzle.asklepios.service.dto.patientActivity;

public record PatientActivitySummaryVM(long openTasks, long dueToday, long overdue, long completed) {
}
