package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
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
 *   <li>{@code true}: only users with "Can Edit Submitted Prescription" (user Encounters Access).</li>
 * </ul>
 */
@Service
public class SubmittedPrescriptionEditPolicy {

    private static final String ENTITY_NAME = "prescription";

    private final boolean enabled;
    private final UserRepository userRepository;

    public SubmittedPrescriptionEditPolicy(
            @Value("${prescription.edit-after-submit.enabled:true}") boolean enabled,
            UserRepository userRepository
    ) {
        this.enabled = enabled;
        this.userRepository = userRepository;
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

    private boolean currentUserHasPermission() {
        return SecurityUtils.getCurrentUserLogin()
                .flatMap(userRepository::findByLogin)
                .map(User::isCanEditSubmittedPrescription)
                .orElse(false);
    }
}
