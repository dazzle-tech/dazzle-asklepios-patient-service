package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import org.springframework.stereotype.Component;

@Component
public class MedicationDaysSupplyResolver {

    private static final int DEFAULT_DAYS_SUPPLY = 30;
    private static final int CHRONIC_DAYS_SUPPLY = 30;

    public int resolve(PatientPrescriptionMedication medication) {
        if (medication == null) {
            return DEFAULT_DAYS_SUPPLY;
        }

        if (Boolean.TRUE.equals(medication.getChronicMedication())) {
            return CHRONIC_DAYS_SUPPLY;
        }

        Double duration = medication.getDuration();
        if (duration == null || duration <= 0) {
            return DEFAULT_DAYS_SUPPLY;
        }

        String durationType = medication.getDurationType();
        if (durationType == null || durationType.isBlank()) {
            return safeInt(duration);
        }

        String normalized = durationType.trim().toLowerCase();
        if (normalized.contains("week")) {
            return safeInt(duration * 7);
        }
        if (normalized.contains("month")) {
            return safeInt(duration * 30);
        }
        if (normalized.contains("year")) {
            return safeInt(duration * 365);
        }

        return safeInt(duration);
    }

    private int safeInt(double value) {
        if (value <= 0) {
            return DEFAULT_DAYS_SUPPLY;
        }
        long rounded = Math.round(value);
        return (int) Math.min(rounded, Integer.MAX_VALUE);
    }
}
