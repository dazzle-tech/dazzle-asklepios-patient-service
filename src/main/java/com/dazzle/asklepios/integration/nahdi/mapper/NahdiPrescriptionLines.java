package com.dazzle.asklepios.integration.nahdi.mapper;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * How a prescription is identified at Nahdi, shared by sending (SendPrescription) and reading back (sales invoices):
 * the prescription reference ({@code pres_RefID}) and the line number ({@code line_No}) of each medication.
 */
public final class NahdiPrescriptionLines {

    private NahdiPrescriptionLines() {}

    /** {@code pres_RefID}: the prescription number (e.g. Rx-119), or the id when there is none. */
    public static String reference(PatientPrescription prescription) {
        return StringUtils.hasText(prescription.getPrescriptionNum())
                ? prescription.getPrescriptionNum()
                : String.valueOf(prescription.getId());
    }

    /**
     * Lines sent to Nahdi, in a fixed order (by id): active medications, plus medications cancelled after the
     * prescription was submitted (sent as cancelled). Medications cancelled before submit were never sent.
     * A medication's {@code line_No} is its position in this list, starting at 1. New medications get higher ids,
     * so a medication keeps its line number on every resend.
     */
    public static List<PatientPrescriptionMedication> sentLines(PatientPrescription prescription) {
        if (prescription.getMedications() == null) {
            return List.of();
        }
        Instant submittedAt = prescription.getSubmitedDate();
        return prescription.getMedications().stream()
                .filter(Objects::nonNull)
                .filter(medication -> !PrescriptionStatus.CANCELLED.equals(medication.getStatus())
                        || (submittedAt != null && medication.getCancelledDate() != null
                        && medication.getCancelledDate().isAfter(submittedAt)))
                .sorted(Comparator.comparing(PatientPrescriptionMedication::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
