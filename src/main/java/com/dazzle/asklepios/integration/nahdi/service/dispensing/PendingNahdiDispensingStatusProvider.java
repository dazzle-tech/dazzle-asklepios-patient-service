package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;
import org.springframework.stereotype.Component;

/**
 * Placeholder until Nahdi confirms where the dispensing status comes from: reports the status as unavailable,
 * so no medication is marked dispensed. Replace this bean with the real implementation.
 */
@Component
public class PendingNahdiDispensingStatusProvider implements NahdiDispensingStatusProvider {

    @Override
    public NahdiDispensingStatus fetch(PatientPrescription prescription) {
        return NahdiDispensingStatus.unavailable("Dispensing status source is not configured yet");
    }
}
