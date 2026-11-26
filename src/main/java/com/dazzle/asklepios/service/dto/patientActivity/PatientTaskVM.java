package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.PatientActivity;

/**
 * A task with the patient it belongs to, for task lists that span patients.
 */
public record PatientTaskVM(PatientActivity task, String patientName, String medicalRecordNumber) {
}
