package com.dazzle.asklepios.web.rest.vm.patient;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class PatientBasicInformationResponseVM {
    private Long id;
    private String firstName;
    private String secondName;
    private String lastName;
    private String medicalRecordNumber;
    private LocalDate dateOfBirth;
    private Gender sexAtBirth;
    private PatientStatus patientStatus;

    public static PatientBasicInformationResponseVM ofEntity(Patient patient) {
        if (patient == null) {
            return null;
        }

        return PatientBasicInformationResponseVM.builder()
                .id(patient.getId())
                .medicalRecordNumber(patient.getMedicalRecordNumber())
                .firstName(patient.getFirstName())
                .secondName(patient.getSecondName())
                .lastName(patient.getLastName())
                .sexAtBirth(patient.getSexAtBirth())
                .dateOfBirth(patient.getDateOfBirth())
                .patientStatus(patient.getPatientStatus())
                .build();
    }
}