package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used while {@code nahdi.dispensing.source} is NONE (the default): reports the status as unavailable, so no
 * medication is marked dispensed. {@link InvoiceNahdiDispensingStatusProvider} replaces it with source=INVOICE.
 */
@Component
@ConditionalOnProperty(name = "nahdi.dispensing.source", havingValue = "NONE", matchIfMissing = true)
public class PendingNahdiDispensingStatusProvider implements NahdiDispensingStatusProvider {

    @Override
    public NahdiDispensingStatus fetch(PatientPrescription prescription) {
        return NahdiDispensingStatus.unavailable("Dispensing status source is not configured yet");
    }
}
