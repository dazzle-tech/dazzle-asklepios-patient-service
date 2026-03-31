package com.dazzle.asklepios.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class MinDateValidator implements ConstraintValidator<MinDate, LocalDate> {
    private LocalDate minDate;

    @Override
    public void initialize(MinDate annotation) {
        this.minDate = LocalDate.parse(annotation.min());
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) return true; // @NotNull يتعامل مع null
        return !value.isBefore(minDate);
    }
}