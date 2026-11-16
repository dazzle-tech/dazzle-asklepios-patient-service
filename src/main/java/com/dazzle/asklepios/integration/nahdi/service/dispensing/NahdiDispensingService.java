package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingSummary;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the dispensing status of submitted prescriptions in sync with Nahdi, and blocks changes to dispensed lines.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NahdiDispensingService {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiDispensingService.class);

    private final NahdiProperties nahdiProperties;
    private final NahdiDispensingStatusProvider dispensingStatusProvider;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;

    /**
     * Fetches the dispensing status from Nahdi and stores it on the prescription medications.
     * When the status is unavailable, the stored status is kept and returned.
     */
    public NahdiDispensingSummary refresh(Long prescriptionId) {
        PatientPrescription prescription = patientPrescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundAlertException("PatientPrescription not found: " + prescriptionId, "prescription", "notfound"));
        List<PatientPrescriptionMedication> medications = patientPrescriptionMedicationRepository.findByPrescriptionHeader_Id(prescriptionId);
        Instant now = Instant.now();

        if (!PrescriptionStatus.SUBMITTED.equals(prescription.getStatus())) {
            return summary(false, "Prescription is not submitted", now, medications);
        }
        if (!nahdiProperties.enabled()) {
            return summary(false, "Nahdi integration is disabled", now, medications);
        }

        NahdiDispensingStatus status;
        try {
            status = dispensingStatusProvider.fetch(prescription);
        } catch (Exception e) {
            LOG.error("[NAHDI][DISPENSING] status fetch failed. prescriptionId={}", prescriptionId, e);
            status = NahdiDispensingStatus.unavailable("Status fetch failed: " + e.getMessage());
        }
        if (status == null || !status.available()) {
            String message = status == null ? "No status returned" : status.message();
            LOG.debug("[NAHDI][DISPENSING] status unavailable, keeping stored status. prescriptionId={}, reason={}", prescriptionId, message);
            return summary(false, message, now, medications);
        }

        Map<Long, PatientPrescriptionMedication> byId = medications.stream()
                .collect(Collectors.toMap(PatientPrescriptionMedication::getId, Function.identity()));
        for (NahdiDispensingStatus.Line line : status.lines()) {
            PatientPrescriptionMedication medication = line.medicationId() == null ? null : byId.get(line.medicationId());
            if (medication == null) {
                continue;
            }
            // A returned medication comes back as not dispensed and becomes editable again.
            medication.setDispensed(line.dispensed());
            medication.setDispensedAt(line.dispensed() ? line.dispensedAt() : null);
            medication.setDispensingReference(line.dispensed() ? line.reference() : null);
        }
        patientPrescriptionMedicationRepository.saveAll(medications);
        LOG.info("[NAHDI][DISPENSING] status refreshed. prescriptionId={}, lines={}", prescriptionId, status.lines().size());
        return summary(true, null, now, medications);
    }

    /**
     * Rejects a change to a medication that the pharmacy already dispensed. The status is refreshed first,
     * so a medication dispensed after the page was opened is still protected.
     */
    public void assertNotDispensed(PatientPrescriptionMedication medication) {
        PatientPrescription header = medication.getPrescriptionHeader();
        if (header == null || !PrescriptionStatus.SUBMITTED.equals(header.getStatus())) {
            return;
        }
        refresh(header.getId());
        if (Boolean.TRUE.equals(medication.getDispensed())) {
            throw new BadRequestAlertException(
                    "This medication was already dispensed by the pharmacy and cannot be changed",
                    "patientPrescriptionMedication",
                    "medicationDispensed"
            );
        }
    }

    private static NahdiDispensingSummary summary(boolean refreshed, String message, Instant checkedAt,
                                                  List<PatientPrescriptionMedication> medications) {
        List<Long> dispensedIds = medications.stream()
                .filter(m -> Boolean.TRUE.equals(m.getDispensed()))
                .map(PatientPrescriptionMedication::getId)
                .toList();
        return new NahdiDispensingSummary(refreshed, message, checkedAt, dispensedIds);
    }
}
