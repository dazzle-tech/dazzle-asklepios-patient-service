package com.dazzle.asklepios.service.dto.patient;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.dazzle.asklepios.util.MinDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientUpdateDTO(

        @NotNull
        Long id,

        @NotEmpty
        String firstName,

        @NotEmpty
        String secondName,
        String thirdName,

        @NotEmpty
        String lastName,

        @NotNull
        Gender sexAtBirth,

        @NotNull
        @PastOrPresent
        @MinDate(min = "1900-01-01", message = "Date of birth cannot be before 01-01-1900.")
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

        @Email
        String email,
        Boolean receiveEmail,
        PreferredWayOfContact preferredWayOfContact,

        String preferredLanguage,
        String emergencyContactName,
        String emergencyContactRelation,
        String emergencyContactPhone,

        String role,
        @NotEmpty
        String maritalStatus,
        String nationality,
        String religion,
        String ethnicity,
        @NotEmpty
        String occupation,
        String responsibleParty,
        String educationalLevel,

        String previousId,
        String archivingNumber,

        String details,
        Boolean isUnknown,

        @NotNull
        Boolean isVerified,

        @NotNull
        Boolean isCompletedPatient,
        Boolean isCchiPatient,
        String documentId,
        SecurityLevel securityAccessLevel,
        BloodGroup bloodGroup,
        String patientConditions

) implements Serializable {

}
