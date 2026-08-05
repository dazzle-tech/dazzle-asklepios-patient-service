package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class CchiBeneficiaryPatientMapper {

    private final ApLovMapperService lovMapperService;

    public Patient toPatient(CchiBeneficiaryData b) {
        if (b == null) {
            return null;
        }

        Patient patient = new Patient();

        patient.setId(null);
        patient.setDocumentId(clean(b.documentId()));

        mapName(patient, b);

        patient.setSexAtBirth(mapGender(b.gender()));
        patient.setDateOfBirth(parseDate(b.dob()));

        patient.setPrimaryMobileNumber(clean(b.contactNumber()));
        patient.setEmail(clean(b.email()));

        patient.setMaritalStatus(mapMaritalStatus(b.martialStatus()));
        patient.setNationality(mapNationality(b.nationality()));
        patient.setReligion(mapReligion(b.religion()));
        patient.setPreferredLanguage(mapPreferredLanguage(b.preferredLanguage()));

        patient.setEmergencyContactPhone(clean(b.emergencyNumber()));

        patient.setPatientClasses("");
        patient.setIsPrivatePatient(false);

        patient.setReceiveSms(false);
        patient.setReceiveEmail(false);
        patient.setPreferredWayOfContact(null);

        patient.setRole("");
        patient.setEthnicity("");
        patient.setResponsibleParty("");
        patient.setEducationalLevel("");

        patient.setPreviousId("");
        patient.setArchivingNumber("");
        patient.setDetails("");

        patient.setIsUnknown(false);
        patient.setIsCchiPatient(true);
        patient.setIsVerified(true);
        patient.setIsCompletedPatient(false);

        patient.setSecurityAccessLevel(SecurityLevel.NORMAL_1);

        return patient;
    }

    private String mapMaritalStatus(String waseelValue) {
        String valueCode = switchValue(
                waseelValue,
                "MARRIED", "MARRIED",
                "M", "MARRIED",
                "SINGLE", "SINGLE",
                "UNMARRIED", "SINGLE",
                "U", "SINGLE",
                "DIVORCED", "DIVORCED",
                "D", "DIVORCED",
                "WIDOWED", "WIDOWED",
                "W", "WIDOWED"
        );

        return lovMapperService.getKeyByLovCodeAndValueCode(
                AsklepiosLovCodes.MARITAL_STATUS,
                valueCode
        );
    }

    private String mapNationality(String waseelValue) {
        String valueCode = switchValue(
                waseelValue,
                "113", "NAT_001",
                "SAUDI", "NAT_001",
                "SAUDI ARABIAN", "NAT_001"
        );

        return lovMapperService.getKeyByLovCodeAndValueCode(
                AsklepiosLovCodes.NATIONALITY,
                valueCode
        );
    }

    private String mapReligion(String waseelValue) {
        String valueCode = switchValue(
                waseelValue,
                "1", "MUS",
                "MUS", "MUS",
                "MUSLIM", "MUS",
                "2", "CHRIST",
                "CHRIST", "CHRIST",
                "CHRISTIAN", "CHRIST",
                "3", "JEW",
                "JEW", "JEW",
                "JEWISH", "JEW",
                "9", "ATHEIST",
                "ATHEIST", "ATHEIST"
        );

        return lovMapperService.getKeyByLovCodeAndValueCode(
                AsklepiosLovCodes.RELIGION,
                valueCode
        );
    }

    private String mapPreferredLanguage(String waseelValue) {
        String valueCode = switchValue(
                waseelValue,

                "AR", "LANG_AR",

                "EN", "LANG_ENG",
                "EN-AU", "LANG_ENG",
                "EN-CA", "LANG_ENG",
                "EN-GB", "LANG_ENG",
                "EN-IN", "LANG_ENG",
                "EN-NZ", "LANG_ENG",
                "EN-SG", "LANG_ENG",
                "EN-US", "LANG_ENG",

                "FR", "LANG_FRN",
                "FR-BE", "LANG_FRN",
                "FR-CH", "LANG_FRN",
                "FR-FR", "LANG_FRN",

                "ES", "LANG_SPAN",
                "ES-AR", "LANG_SPAN",
                "ES-ES", "LANG_SPAN",
                "ES-UY", "LANG_SPAN",

                "RU", "LANG_RUSS",
                "RU-RU", "LANG_RUSS",

                "PT", "LANG_PORT",
                "PT-BR", "LANG_PORT",

                "TR", "LANG_TURK",

                "BN", "LANG_BN",
                "CS", "LANG_CS",
                "DA", "LANG_DA",

                "DE", "LANG_DE",
                "DE-AT", "LANG_DE",
                "DE-CH", "LANG_DE",
                "DE-DE", "LANG_DE",

                "EL", "LANG_EL",
                "FI", "LANG_FI",

                "FY", "LANG_FY",
                "FY-NL", "LANG_FY",

                "HI", "LANG_HI",
                "HR", "LANG_HR",

                "IT", "LANG_IT",
                "IT-CH", "LANG_IT",
                "IT-IT", "LANG_IT",

                "JA", "LANG_JA",
                "KO", "LANG_KO",

                "NL", "LANG_NL",
                "NL-BE", "LANG_NL",
                "NL-NL", "LANG_NL",

                "NO", "LANG_NO",
                "NO-NO", "LANG_NO",

                "PA", "LANG_PA",

                "SR", "LANG_SR",
                "SR-RS", "LANG_SR",

                "SV", "LANG_SV",
                "SV-SE", "LANG_SV",

                "TE", "LANG_TE",

                "ZH", "LANG_ZH",
                "ZH-CN", "LANG_ZH",
                "ZH-HK", "LANG_ZH",
                "ZH-SG", "LANG_ZH",
                "ZH-TW", "LANG_ZH"
        );

        return lovMapperService.getKeyByLovCodeAndValueCode(
                AsklepiosLovCodes.LANG,
                valueCode
        );
    }

    private Gender mapGender(String value) {
        if (isBlank(value)) {
            return null;
        }

        return switch (value.trim().toUpperCase()) {
            case "MALE", "M" -> Gender.MALE;
            case "FEMALE", "F" -> Gender.FEMALE;
            default -> null;
        };
    }

    private void mapName(Patient patient, CchiBeneficiaryData b) {
        String firstName = clean(b.firstName());
        String middleName = clean(b.middleName());
        String lastName = clean(b.lastName());
        String familyName = clean(b.familyName());
        String fullName = clean(b.fullName());

        if (isBlank(firstName)
                && isBlank(middleName)
                && isBlank(lastName)
                && isBlank(familyName)
                && !isBlank(fullName)) {

            mapFullName(patient, fullName);
            return;
        }

        patient.setFirstName(firstName);
        patient.setSecondName(middleName);
        patient.setThirdName("");
        patient.setLastName(!isBlank(familyName) ? familyName : lastName);
    }

    private void mapFullName(Patient patient, String fullName) {
        String[] parts = fullName.trim().split("\\s+");

        patient.setFirstName("");
        patient.setSecondName("");
        patient.setThirdName("");
        patient.setLastName("");

        if (parts.length == 1) {
            patient.setFirstName(parts[0]);
        } else if (parts.length == 2) {
            patient.setFirstName(parts[0]);
            patient.setLastName(parts[1]);
        } else if (parts.length == 3) {
            patient.setFirstName(parts[0]);
            patient.setSecondName(parts[1]);
            patient.setLastName(parts[2]);
        } else {
            patient.setFirstName(parts[0]);
            patient.setSecondName(parts[1]);
            patient.setThirdName(parts[2]);
            patient.setLastName(
                    String.join(" ", Arrays.copyOfRange(parts, 3, parts.length))
            );
        }
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        String date = value.trim();

        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(
                    date,
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
            );
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(
                    date,
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")
            );
        } catch (Exception ignored) {
        }

        return null;
    }

    private String switchValue(String input, String... pairs) {
        if (isBlank(input)) {
            return "";
        }

        String normalized = input.trim().toUpperCase();

        for (int i = 0; i < pairs.length; i += 2) {
            if (normalized.equals(pairs[i])) {
                return pairs[i + 1];
            }
        }

        return normalized;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}