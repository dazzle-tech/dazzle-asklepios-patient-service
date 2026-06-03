package com.dazzle.asklepios.web.rest.vm.patient;

public record CreatePasswordKeyValidationVM(
    boolean valid,
    boolean activated,
    boolean passwordAlreadySet,
    String message
) {}

