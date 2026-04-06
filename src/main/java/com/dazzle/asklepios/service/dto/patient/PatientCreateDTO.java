package com.dazzle.asklepios.service.dto.patient;

import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientCreateDTO(

        @NotEmpty
        String firstName,

        String secondName,
        String thirdName,

        @NotEmpty
        String lastName,

        @NotNull
        Gender sexAtBirth,

        @NotNull
        @PastOrPresent
        LocalDate dateOfBirth,

        String patientClasses,
        Boolean isPrivatePatient,

        String firstNameSecondaryLang,
        String secondNameSecondaryLang,
        String thirdNameSecondaryLang,
        String lastNameSecondaryLang,

        @NotEmpty
        String primaryMobileNumber,

        Boolean receiveSms,
        String secondMobileNumber,
        String homePhone,
        String workPhone,

        @NotEmpty @Email
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
        SecurityLevel securityAccessLevel)

     implements Serializable {

}