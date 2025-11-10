package com.dazzle.asklepios.web.rest.errors;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ProblemDetail;

/**
 * Exception used to send a Not Found (404) response with structured problem details.
 * Uses {@link ErrorResponseException} to provide RFC 7807 problem details.
 */
@SuppressWarnings("java:S110")
public class NotFoundAlertException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    public NotFoundAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey);
    }

    public NotFoundAlertException(URI type, String defaultMessage, String entityName, String errorKey) {
        super(HttpStatus.NOT_FOUND, asProblemDetail(type, defaultMessage, entityName, errorKey), null);
    }

    private static ProblemDetail asProblemDetail(URI type, String defaultMessage, String entityName, String errorKey) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, defaultMessage);
        problemDetail.setType(type);
        problemDetail.setTitle("Entity not found");
        problemDetail.setProperty("entityName", entityName);
        problemDetail.setProperty("errorKey", errorKey);
        return problemDetail;
    }
}
