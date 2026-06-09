package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;

import java.util.Map;

public final class WaseelPracticeCodeMapper {

    private WaseelPracticeCodeMapper() {
    }

    private static final Map<String, String> SUB_SPECIALTY_TO_WASEEL_CODE = Map.ofEntries(
            Map.entry("SUB_SPC_001", "06.01"), // Family Medicine
            Map.entry("SUB_SPC_002", "08.00"), // Internal Medicine
            Map.entry("SUB_SPC_003", "14.00"), // Pediatrics
            Map.entry("SUB_SPC_004", "08.06"), // Geriatrics
            Map.entry("SUB_SPC_005", "19.08"), // General Surgery
            Map.entry("SUB_SPC_006", "19.25"), // Cardiothoracic Surgery
            Map.entry("SUB_SPC_007", "19.11"), // Neurosurgery
            Map.entry("SUB_SPC_008", "12.02"), // Orthopedic Surgery
            Map.entry("SUB_SPC_009", "19.14"), // Plastic and Reconstructive Surgery
            Map.entry("SUB_SPC_010", "19.19"), // Vascular Surgery
            Map.entry("SUB_SPC_011", "20.00"), // Urology
            Map.entry("SUB_SPC_012", "05.06"), // ENT
            Map.entry("SUB_SPC_013", "05.05"), // Oral and Maxillofacial Surgery
            Map.entry("SUB_SPC_014", "08.02"), // Cardiology
            Map.entry("SUB_SPC_015", "08.04"), // Endocrinology
            Map.entry("SUB_SPC_016", "08.05"), // Gastroenterology
            Map.entry("SUB_SPC_017", "08.07"), // Hematology
            Map.entry("SUB_SPC_018", "08.08"), // Infectious Disease
            Map.entry("SUB_SPC_019", "08.09"), // Nephrology
            Map.entry("SUB_SPC_020", "08.18"), // Neurology
            Map.entry("SUB_SPC_021", "08.11"), // Oncology
            Map.entry("SUB_SPC_022", "08.13"), // Pulmonology
            Map.entry("SUB_SPC_023", "08.14"), // Rheumatology
            Map.entry("SUB_SPC_024", "13.00"), // Pathology
            Map.entry("SUB_SPC_025", "18.00"), // Radiology
            Map.entry("SUB_SPC_026", "08.10"), // Nuclear Medicine
            Map.entry("SUB_SPC_028", "04.00"), // Emergency Medicine
            Map.entry("SUB_SPC_029", "21.00"), // Critical Care
            Map.entry("SUB_SPC_030", "19.18"), // Trauma Surgery
            Map.entry("SUB_SPC_031", "17.08"), // Psychiatry
            Map.entry("SUB_SPC_032", "17.02"), // Child and Adolescent Psychiatry
            Map.entry("SUB_SPC_033", "17.04"), // Forensic Psychiatry
            Map.entry("SUB_SPC_034", "16.01"), // Physical Medicine and Rehabilitation
            Map.entry("SUB_SPC_036", "10.00"), // OB/GYN
            Map.entry("SUB_SPC_037", "10.09"), // Maternal-Fetal Medicine
            Map.entry("SUB_SPC_038", "10.07"), // Reproductive Endocrinology and Infertility
            Map.entry("SUB_SPC_039", "10.01"), // Gynecologic Oncology
            Map.entry("SUB_SPC_040", "14.20"), // Pediatric Cardiology
            Map.entry("SUB_SPC_041", "14.04"), // Pediatric Endocrinology
            Map.entry("SUB_SPC_042", "14.05"), // Pediatric Gastroenterology
            Map.entry("SUB_SPC_043", "15.06"), // Pediatric Hematology/Oncology
            Map.entry("SUB_SPC_044", "14.13"), // Pediatric Nephrology
            Map.entry("SUB_SPC_045", "14.16"), // Pediatric Neurology
            Map.entry("SUB_SPC_046", "14.14"), // Pediatric Pulmonology
            Map.entry("SUB_SPC_047", "14.11"), // Pediatric Infectious Disease
            Map.entry("SUB_SPC_048", "02.01"), // Public Health Medicine
            Map.entry("SUB_SPC_049", "16.02"), // Occupational Medicine
            Map.entry("SUB_SPC_051", "19.02"), // Bariatric Surgery
            Map.entry("SUB_SPC_052", "19.20"), // Colorectal Surgery
            Map.entry("SUB_SPC_053", "19.21"), // Transplant Surgery
            Map.entry("SUB_SPC_054", "08.16"), // Sports Medicine
            Map.entry("SUB_SPC_055", "08.15"), // Sleep Medicine
            Map.entry("SUB_SPC_056", "17.05"), // Geriatric Psychiatry
            Map.entry("SUB_SPC_058", "08.12"), // Hospice and Palliative Medicine
            Map.entry("SUB_SPC_060", "03.00"), // Dermatology
            Map.entry("SUB_SPC_061", "11.07"), // Ophthalmology
            Map.entry("SUB_SPC_063", "01.00")  // Anesthesiology
    );

    private static final Map<String, String> EDU_LEVEL_TO_WASEEL_CODE = Map.ofEntries(
            Map.entry("NO_EDU", "08.26"),
            Map.entry("PRIMARY_EDU", "08.26"),
            Map.entry("SECONDARY_EDU", "08.26"),
            Map.entry("TECH_EDU", "08.26"),
            Map.entry("BACHELOR_EDU", "08.26"),
            Map.entry("MASTER_EDU", "08.26"),
            Map.entry("PHD_EDU", "08.26")
    );

    public static String mapSubSpecialtyCode(String subSpecialty) {
        String code = SUB_SPECIALTY_TO_WASEEL_CODE.get(clean(subSpecialty));

        if (code == null) {
            throw new BadRequestAlertException(
                    "No Waseel mapping found for practitioner sub specialty: " + subSpecialty,
                    "preAuthorization",
                    "practitioner.subSpecialty.waseelMapping.notFound"
            );
        }

        return code;
    }

    public static String mapEducationCode(String educationalLevel, String fallbackSpecialityCode) {
        String code = EDU_LEVEL_TO_WASEEL_CODE.get(clean(educationalLevel));

        if (code != null) {
            return code;
        }

        if (fallbackSpecialityCode != null && !fallbackSpecialityCode.isBlank()) {
            return fallbackSpecialityCode;
        }

        throw new BadRequestAlertException(
                "No Waseel mapping found for practitioner educational level: " + educationalLevel,
                "preAuthorization",
                "practitioner.educationalLevel.waseelMapping.notFound"
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final Map<String, String> SUB_SPECIALTY_TO_DISPLAY = Map.ofEntries(
            Map.entry("SUB_SPC_001", "Family Medicine")
    );

    public static String mapSubSpecialtyDisplay(String subSpecialty) {
        String display = SUB_SPECIALTY_TO_DISPLAY.get(clean(subSpecialty));

        if (display == null) {
            throw new BadRequestAlertException(
                    "No Waseel display mapping found for practitioner sub specialty: " + subSpecialty,
                    "preAuthorization",
                    "practitioner.subSpecialty.displayMapping.notFound"
            );
        }

        return display;
    }
}