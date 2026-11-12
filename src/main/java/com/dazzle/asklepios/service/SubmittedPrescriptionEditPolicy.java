package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single place that decides who may change a prescription after it was submitted: add, edit or cancel a
 * medication, or resend it to the pharmacy. This applies in the visit and on a closed visit opened from the EMR.
 * Drafts are not affected. The UI asks {@link #canCurrentUserEdit()} through the API, so it holds no logic of its own.
 *
 * <ul>
 *   <li>{@code prescription.edit-after-submit.enabled=false}: nobody may change a submitted prescription.</li>
 *   <li>{@code true}: allowed for users who pass {@link #currentUserHasPermission()}.</li>
 * </ul>
 */
@Service
public class SubmittedPrescriptionEditPolicy {

    private static final String ENTITY_NAME = "prescription";

    private final boolean enabled;

    public SubmittedPrescriptionEditPolicy(@Value("${prescription.edit-after-submit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean canCurrentUserEdit() {
        return enabled && currentUserHasPermission();
    }

    public void assertCanEdit(PatientPrescription prescription) {
        if (prescription == null || !PrescriptionStatus.SUBMITTED.equals(prescription.getStatus())) {
            return;
        }
        if (!canCurrentUserEdit()) {
            throw new BadRequestAlertException(
                    "You are not allowed to change a prescription after it was submitted.",
                    ENTITY_NAME,
                    "editAfterSubmitNotAllowed"
            );
        }
    }

    /**
     * The permission check, still to be decided. Every user passes for now. Put the check here when it is
     * decided (e.g. a user flag like canUnCompleteEncounter, or a role screen permission).
     */
    private boolean currentUserHasPermission() {
        return true;
    }
}
