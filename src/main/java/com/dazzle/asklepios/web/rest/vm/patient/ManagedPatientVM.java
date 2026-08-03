package com.dazzle.asklepios.web.rest.vm.patient;

import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class ManagedPatientVM {

    public static final int PASSWORD_MIN_LENGTH = 4;

    public static final int PASSWORD_MAX_LENGTH = 100;

    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;

    public ManagedPatientVM() {
    }
}
