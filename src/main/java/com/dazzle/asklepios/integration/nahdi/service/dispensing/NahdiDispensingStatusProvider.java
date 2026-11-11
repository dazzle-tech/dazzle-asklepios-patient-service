package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;

/**
 * Source of the dispensing status of a prescription at Nahdi.
 * <p>
 * The source is not decided yet (invoices or a dedicated status API); an implementation maps Nahdi's answer
 * to our prescription medications. It must not throw: return {@link NahdiDispensingStatus#unavailable(String)}
 * when the status cannot be obtained.
 */
public interface NahdiDispensingStatusProvider {

    NahdiDispensingStatus fetch(PatientPrescription prescription);
}
