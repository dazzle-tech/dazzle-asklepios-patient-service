package com.dazzle.asklepios.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MinDateValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinDate {
    String message() default "Invalid date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String min() default "1900-01-01";
}