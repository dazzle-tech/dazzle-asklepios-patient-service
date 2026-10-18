package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

        BigDecimal duration = medication.getDuration();
        if (duration == null || duration.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_DAYS_SUPPLY;
        }

        String durationType = medication.getDurationType();
        if (durationType == null || durationType.isBlank()) {
            return safeInt(duration);
        }

        String normalized = durationType.trim().toLowerCase();
        if (normalized.contains("week")) {
            return safeInt(duration.multiply(BigDecimal.valueOf(7)));
        }
        if (normalized.contains("month")) {
            return safeInt(duration.multiply(BigDecimal.valueOf(30)));
        }
        if (normalized.contains("year")) {
            return safeInt(duration.multiply(BigDecimal.valueOf(365)));
        }

        return safeInt(duration);
    }

    private int safeInt(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_DAYS_SUPPLY;
        }
        long rounded = value.setScale(0, RoundingMode.HALF_UP).longValue();
        return (int) Math.min(rounded, Integer.MAX_VALUE);
    }
}
