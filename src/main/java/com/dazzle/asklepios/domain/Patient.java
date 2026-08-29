package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.AbstractAuditingEntity;
import com.dazzle.asklepios.domain.PatientDocument;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

import com.dazzle.asklepios.domain.enumeration.BloodGroup;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medical_record_number", insertable = false, updatable = false)
    @Generated(GenerationTime.INSERT)
    private String medicalRecordNumber;

    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<PatientDocument> patientDocuments;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "second_name", length = 100)
    private String secondName;

    @Column(name = "third_name", length = 100)
    private String thirdName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex_at_birth", length = 20)
    private Gender sexAtBirth;

    @PastOrPresent
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "patient_classes", length = 50)
    private String patientClasses;

    @Column(name = "is_private_patient")
    private Boolean isPrivatePatient;

    @Column(name = "first_name_secondary_lang", length = 100)
    private String firstNameSecondaryLang;

    @Column(name = "second_name_secondary_lang", length = 100)
    private String secondNameSecondaryLang;

    @Column(name = "third_name_secondary_lang", length = 100)
    private String thirdNameSecondaryLang;

    @Column(name = "last_name_secondary_lang", length = 100)
    private String lastNameSecondaryLang;

    @Column(name = "primary_mobile_number", length = 20)
    private String primaryMobileNumber;

    @Column(name = "receive_sms")
    private Boolean receiveSms;

    @Column(name = "second_mobile_number", length = 20)
    private String secondMobileNumber;

    @Column(name = "home_phone", length = 20)
    private String homePhone;

    @Column(name = "work_phone", length = 20)
    private String workPhone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "receive_email")
    private Boolean receiveEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_way_of_contact", length = 50)
    private PreferredWayOfContact preferredWayOfContact;

    @Column(name = "preferred_language", length = 100)
    private String preferredLanguage;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relation", length = 100)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "role", length = 50)
    private String role;

    @NotNull
    @Column(name = "marital_status", length = 50)
    private String maritalStatus;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "religion", length = 100)
    private String religion;

    @Column(name = "ethnicity", length = 100)
    private String ethnicity;

    @NotNull
    @Column(name = "occupation", length = 150)
    private String occupation;

    @Column(name = "responsible_party", length = 150)
    private String responsibleParty;

    @Column(name = "educational_level", length = 100)
    private String educationalLevel;

    @Column(name = "previous_id", length = 50)
    private String previousId;

    @Column(name = "archiving_number", length = 50)
    private String archivingNumber;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "is_unknown", nullable = false)
    private Boolean isUnknown;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    @Column(name = "is_completed_patient", nullable = false)
    private Boolean isCompletedPatient;

    @Column(name = "is_cchi_patient", nullable = false)
    @Builder.Default
    private Boolean isCchiPatient = false;

    @Column(name = "document_id", unique = true, length = 100)
    private String documentId;

    @Size(max = 20)
    @Column(name = "reset_key")
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    @JsonIgnore
    @Size(min = 60, max = 60)
    @Column(name = "password_hash")
    private String password;

    @NotNull
    private boolean activated = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_access_level")
    private SecurityLevel securityAccessLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_status", length = 30, nullable = false)
    @Builder.Default
    private PatientStatus patientStatus = PatientStatus.ACTIVE;

    @Column(name = "merged_into_patient_id")
    private Long mergedIntoPatientId;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "merged_by", length = 50)
    private String mergedBy;

    @Column(name = "merge_note", length = 1000)
    private String mergeNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 20)
    private BloodGroup bloodGroup;

    @Column(name = "patient_conditions", columnDefinition = "text")
    private String patientConditions;

    @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
    @Column(length = 4)
    private String pin;

    @AssertTrue(message = "When patient is not unknown, firstName, lastName, sexAtBirth, dateOfBirth and primaryMobileNumber are required")
    public boolean isValidWhenNotUnknown() {
        if (Boolean.TRUE.equals(isUnknown)) {
            return true;
        }

        return firstName != null
                && lastName != null
                && sexAtBirth != null
                && dateOfBirth != null
                && primaryMobileNumber != null;
    }
}
