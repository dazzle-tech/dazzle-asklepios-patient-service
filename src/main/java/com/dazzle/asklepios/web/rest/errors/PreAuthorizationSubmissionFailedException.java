package com.dazzle.asklepios.web.rest.errors;

/**
 * Waseel rejected or could not accept a pre-authorization payload.
 * The clinical order is kept so the user can retry from tracking.
 */
public class PreAuthorizationSubmissionFailedException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    public PreAuthorizationSubmissionFailedException(
            String defaultMessage,
            Long encounterId,
            Long preAuthorizationId
    ) {
        super(
                defaultMessage,
                "preAuthorization",
                "waseel.submit.failed"
        );
        getBody().setProperty("canResubmit", Boolean.TRUE);
        getBody().setProperty("encounterId", encounterId);
        getBody().setProperty("preAuthorizationId", preAuthorizationId);
    }
}
