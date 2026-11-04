package com.dazzle.asklepios.web.rest.errors;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Tells expected business errors (4xx) apart from unexpected system errors, so the former are logged as WARN.
 */
public final class BusinessExceptions {

    private BusinessExceptions() {}

    public static boolean isBusinessException(Throwable ex) {
        if (ex instanceof PatientAlreadyActiveException) {
            return true;
        }
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().is4xxClientError();
        }
        ResponseStatus responseStatus = findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        return responseStatus != null && responseStatus.code().is4xxClientError();
    }
}
