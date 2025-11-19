package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.PreferredWayOfContact;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.time.LocalDate;

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

    @Column(name = "mrn", nullable = false, length = 50)
    private String mrn;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "second_name", length = 100)
    private String secondName;

    @Column(name = "third_name", length = 100)
    private String thirdName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "sex_at_birth", nullable = false, length = 20)
    private Gender sexAtBirth;

    @Column(name = "date_of_birth", nullable = false)
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

    @Column(name = "preferred_way_of_contact", length = 50)
    private PreferredWayOfContact preferredWayOfContact;

    @Column(name = "native_language", length = 100)
    private String nativeLanguage;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relation", length = 100)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "marital_status", length = 50)
    private String maritalStatus;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "religion", length = 100)
    private String religion;

    @Column(name = "ethnicity", length = 100)
    private String ethnicity;

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

    @Column(name = "is_unknown")
    private Boolean isUnknown;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "is_completed_patient")
    private Boolean isCompletedPatient;

    @Enumerated(EnumType.STRING)
    @Column(name="security_access_level")
    private SecurityLevel securityAccessLevel;
}
