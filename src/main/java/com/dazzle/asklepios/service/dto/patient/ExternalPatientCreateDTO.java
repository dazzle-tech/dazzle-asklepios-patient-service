package com.dazzle.asklepios.service.dto.patient;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.dazzle.asklepios.service.helper.PatientCitizenFlag;
import com.dazzle.asklepios.util.MinDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalPatientCreateDTO(

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
        Boolean isCchiPatient,
        String documentId,
        SecurityLevel securityAccessLevel,
        BloodGroup bloodGroup,
        String patientConditions,

        @NotNull
        Long countryId,

        @NotNull
        DocumentType primaryDocumentType,

        @NotEmpty
        String primaryDocumentNumber

) implements Serializable {

    public PatientCreateDTO toPatientCreateDTO() {
        String resolvedDocumentId = documentId == null || documentId.isBlank()
                ? primaryDocumentNumber
                : documentId;

        return new PatientCreateDTO(
                firstName,
                secondName,
                thirdName,
                lastName,
                sexAtBirth,
                dateOfBirth,
                patientClasses,
                isPrivatePatient,
                firstNameSecondaryLang,
                secondNameSecondaryLang,
                thirdNameSecondaryLang,
                lastNameSecondaryLang,
                primaryMobileNumber,
                receiveSms,
                secondMobileNumber,
                homePhone,
                workPhone,
                email,
                receiveEmail,
                preferredWayOfContact,
                preferredLanguage,
                emergencyContactName,
                emergencyContactRelation,
                emergencyContactPhone,
                role,
                maritalStatus,
                nationality,
                religion,
                ethnicity,
                occupation,
                responsibleParty,
                educationalLevel,
                previousId,
                archivingNumber,
                details,
                isUnknown,
                isVerified,
                isCompletedPatient,
                isCchiPatient,
                resolvedDocumentId,
                securityAccessLevel,
                bloodGroup,
                patientConditions,
                PatientCitizenFlag.fromDocumentType(primaryDocumentType)
        );
    }
}
