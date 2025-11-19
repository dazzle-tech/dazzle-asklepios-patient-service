package com.dazzle.asklepios.web.rest.vm.patient;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;

import java.io.Serializable;
import java.time.LocalDate;

public record PatientResponseVM(
        Long id,
        String mrn,

        String firstName,
        String secondName,
        String thirdName,
        String lastName,
        Gender sexAtBirth,
        LocalDate dateOfBirth,
        SecurityLevel securityAccessLevel,
        String patientClasses,
        Boolean isPrivatePatient,

        String firstNameSecondaryLang,
        String secondNameSecondaryLang,
        String thirdNameSecondaryLang,
        String lastNameSecondaryLang,

        String primaryMobileNumber,
        Boolean receiveSms,
        String secondMobileNumber,
        String homePhone,
        String workPhone,
        String email,
        Boolean receiveEmail,
        PreferredWayOfContact preferredWayOfContact,

        String nativeLanguage,
        String emergencyContactName,
        String emergencyContactRelation,
        String emergencyContactPhone,

        String role,
        String maritalStatus,
        String nationality,
        String religion,
        String ethnicity,
        String occupation,
        String responsibleParty,
        String educationalLevel,

        String previousId,
        String archivingNumber,

        String details,
        Boolean isUnknown,
        Boolean isVerified,
        Boolean isCompletedPatient,

        java.time.Instant createdDate,
        String createdBy
) implements Serializable {

    public static PatientResponseVM ofEntity(Patient patient) {
        return new PatientResponseVM(
                patient.getId(),
                patient.getMrn(),

                patient.getFirstName(),
                patient.getSecondName(),
                patient.getThirdName(),
                patient.getLastName(),
                patient.getSexAtBirth(),
                patient.getDateOfBirth(),
                patient.getSecurityAccessLevel(),
                patient.getPatientClasses(),
                patient.getIsPrivatePatient(),

                patient.getFirstNameSecondaryLang(),
                patient.getSecondNameSecondaryLang(),
                patient.getThirdNameSecondaryLang(),
                patient.getLastNameSecondaryLang(),

                patient.getPrimaryMobileNumber(),
                patient.getReceiveSms(),
                patient.getSecondMobileNumber(),
                patient.getHomePhone(),
                patient.getWorkPhone(),
                patient.getEmail(),
                patient.getReceiveEmail(),
                patient.getPreferredWayOfContact(),

                patient.getNativeLanguage(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactRelation(),
                patient.getEmergencyContactPhone(),

                patient.getRole(),
                patient.getMaritalStatus(),
                patient.getNationality(),
                patient.getReligion(),
                patient.getEthnicity(),
                patient.getOccupation(),
                patient.getResponsibleParty(),
                patient.getEducationalLevel(),

                patient.getPreviousId(),
                patient.getArchivingNumber(),

                patient.getDetails(),
                patient.getIsUnknown(),
                patient.getIsVerified(),
                patient.getIsCompletedPatient(),
                patient.getCreatedDate(),
                patient.getCreatedBy()
        );
    }
}
