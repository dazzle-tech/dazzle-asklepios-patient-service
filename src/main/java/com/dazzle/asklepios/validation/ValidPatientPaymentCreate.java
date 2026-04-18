package com.dazzle.asklepios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Documented
@Constraint(validatedBy = PatientPaymentCreateValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPatientPaymentCreate {

    String message() default "Invalid patient payment request";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}