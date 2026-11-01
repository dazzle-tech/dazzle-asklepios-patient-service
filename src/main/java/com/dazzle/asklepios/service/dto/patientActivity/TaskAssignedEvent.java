package com.dazzle.asklepios.service.dto.patientActivity;

/**
 * Published when a task gets a new assignee; the notice goes out once the change is committed.
 */
public record TaskAssignedEvent(Long taskId, String assignee, String assignedBy) {
}
