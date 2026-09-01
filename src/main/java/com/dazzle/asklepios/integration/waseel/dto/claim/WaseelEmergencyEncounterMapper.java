package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.DischargeType;
import com.dazzle.asklepios.domain.enumeration.EmergencyLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Maps local emergency visit data to Waseel {@code claimEncounter.encounterEmergency}.
 * That nested object is the "Encounter Emergency" form section in the Waseel docs.
 */
public final class WaseelEmergencyEncounterMapper {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private static final DateTimeFormatter NPHIES_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String DEFAULT_ARRIVAL = "other";
    private static final Set<String> ARRIVAL_CODES = Set.of(
            "GEMSA", "MOHA", "GCDA", "GMA", "GPA", "EMSAA", "ACDA", "AMA",
            "PV", "OGV", "other"
    );

    private WaseelEmergencyEncounterMapper() {}

    public static WaseelEncounterEmergency toEncounterEmergency(
            PatientEncounter encounter,
            EmergencyTriage triage,
            LocalDate claimDate
    ) {
        return new WaseelEncounterEmergency(
                arrivalCode(encounter),
                emergencyServiceStart(encounter, claimDate),
                departmentDisposition(encounter),
                triageCategory(triage),
                triageDate(encounter, triage, claimDate)
        );
    }

    public static String arrivalCode(PatientEncounter encounter) {
        String origin = encounter == null ? null : encounter.getOriginType();
        if (origin == null || origin.isBlank()) {
            return DEFAULT_ARRIVAL;
        }
        String trimmed = origin.trim();
        for (String code : ARRIVAL_CODES) {
            if (code.equalsIgnoreCase(trimmed)) {
                return code;
            }
        }
        return DEFAULT_ARRIVAL;
    }

    public static String triageCategory(EmergencyTriage triage) {
        EmergencyLevel level = triage == null ? null : triage.getEmergencyLevel();
        if (level == null) {
            return "SER";
        }
        return switch (level) {
            case RESUSCITATION -> "IR";
            case EMERGENT -> "VU";
            case URGENT -> "U";
            case LESS_URGENT -> "SER";
            case NON_URGENT -> "NU";
        };
    }

    public static String departmentDisposition(PatientEncounter encounter) {
        DischargeType dischargeType = encounter == null ? null : encounter.getDischargeType();
        if (dischargeType == null) {
            return "NAD";
        }
        return switch (dischargeType) {
            case DISCHARGED_TO_HOME -> "NAD";
            case TRANSFERRED_TO_ANOTHER_FACILITY -> "NAR";
            case LEFT_AGAINST_MEDICAL_ADVICE_AMA -> "LAOR";
            case DECEASED -> "DED";
            case LEFT_WITHOUT_BEING_SEEN -> "DNW";
        };
    }

    public static String triageDate(PatientEncounter encounter, EmergencyTriage triage, LocalDate fallbackDate) {
        if (triage != null) {
            if (triage.getCompletedDate() != null) {
                return formatInstant(triage.getCompletedDate());
            }
            if (triage.getCreatedDate() != null) {
                return formatInstant(triage.getCreatedDate());
            }
        }
        return encounterDateTime(encounter, fallbackDate);
    }

    public static String emergencyServiceStart(PatientEncounter encounter, LocalDate fallbackDate) {
        if (encounter != null && encounter.getStartedDate() != null) {
            return formatInstant(encounter.getStartedDate());
        }
        return encounterDateTime(encounter, fallbackDate);
    }

    private static String encounterDateTime(PatientEncounter encounter, LocalDate fallbackDate) {
        LocalDate date = encounter != null && encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : fallbackDate;
        LocalTime time = encounter != null && encounter.getEncounterTime() != null
                ? encounter.getEncounterTime()
                : LocalTime.NOON;
        if (date == null) {
            date = LocalDate.now(RIYADH);
        }
        return format(ZonedDateTime.of(LocalDateTime.of(date, time), RIYADH));
    }

    private static String formatInstant(Instant instant) {
        return format(instant.atZone(RIYADH));
    }

    private static String format(ZonedDateTime dateTime) {
        return dateTime.format(NPHIES_DATE_TIME);
    }
}
