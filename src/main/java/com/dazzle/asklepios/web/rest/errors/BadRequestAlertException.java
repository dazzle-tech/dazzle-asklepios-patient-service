package com.dazzle.asklepios.web.rest.errors;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Exception used to send a Bad Request (400) response with structured problem details.
 * <p>
 * Supports additional metadata such as entityName and errorKey.
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class BadRequestAlertException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    private final String entityName;
    private final String errorKey;

    /**
     * Default constructor using BAD_REQUEST status.
     */
    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey);
    }

    /**
     * Default BAD_REQUEST constructor with type.
     */
    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey) {
        super(
                HttpStatus.BAD_REQUEST,
                ProblemDetailWithCause.ProblemDetailWithCauseBuilder.instance()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withType(type)
                        .withTitle(defaultMessage)
                        .withProperty("message", "error." + errorKey)
                        .withProperty("params", entityName)
                        .build(),
                null
        );
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    /**
     * Protected constructor that allows specifying a custom HTTP status.
     * Used by subclasses like {@link NotFoundAlertException}.
     */
    protected BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, HttpStatus status) {
        super(
                status,
                ProblemDetailWithCause.ProblemDetailWithCauseBuilder.instance()
                        .withStatus(status.value())
                        .withType(type)
                        .withTitle(defaultMessage)
                        .withProperty("message", "error." + errorKey)
                        .withProperty("params", entityName)
                        .build(),
                null
        );
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public ProblemDetailWithCause getProblemDetailWithCause() {
        return (ProblemDetailWithCause) this.getBody();
    }
}
