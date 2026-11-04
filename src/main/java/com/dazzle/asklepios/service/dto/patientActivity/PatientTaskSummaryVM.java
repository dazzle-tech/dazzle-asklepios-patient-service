package com.dazzle.asklepios.service.dto.patientActivity;

public record PatientTaskSummaryVM(long open, long dueToday, long overdue, long unassigned, long completedToday) {
}
