package com.dazzle.asklepios.web.rest.errors;

import org.springframework.http.HttpStatus;

public class ConflictAlertException extends BadRequestAlertException {

    public ConflictAlertException(String defaultMessage, String entityName, String errorKey) {
        super(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, HttpStatus.CONFLICT);
    }
}
